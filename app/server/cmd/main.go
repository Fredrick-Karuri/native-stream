// cmd/main.go — Phase 3: fully wired with Discovery Engine.

package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/fredrick-karuri/nativestream/server/api"
	"github.com/fredrick-karuri/nativestream/server/config"
	"github.com/fredrick-karuri/nativestream/server/discovery"
	"github.com/fredrick-karuri/nativestream/server/discovery/crawlers"
	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/proxy"
	"github.com/fredrick-karuri/nativestream/server/service"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
)

func main() {
	if len(os.Args) > 1 {
		switch os.Args[1] {
		case "--install-service":
			binary := ""
			if len(os.Args) > 2 {
				binary = os.Args[2]
			}
			if err := service.Install(binary); err != nil {
				log.Fatalf("install-service: %v", err)
			}
			return
		case "--uninstall-service":
			if err := service.Uninstall(); err != nil {
				log.Fatalf("uninstall-service: %v", err)
			}
			return
		case "--help", "-h":
			fmt.Println("NativeStream Server v3.0")
			fmt.Println("Usage:")
			fmt.Println("  nativestream-server                     Start")
			fmt.Println("  nativestream-server --install-service   Register launchd service")
			fmt.Println("  nativestream-server --uninstall-service Remove launchd service")
			return
		}
	}

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	log.Printf("NativeStream Server v3.0 — http://%s", cfg.Server.Addr())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// ── Store ──────────────────────────────────────────────────────────────────
	s := store.New(cfg.Store.SnapshotPath)
	if err := s.Load(); err != nil {
		log.Printf("⚠ store load: %v (starting fresh)", err)
	}
	total, healthy := s.Count()
	log.Printf("Store: %d channels (%d healthy)", total, healthy)

	// ── Validator ──────────────────────────────────────────────────────────────
	v := validator.New(validator.Config{
		Interval:        cfg.Probe.Interval,
		Timeout:         cfg.Probe.Timeout,
		Concurrency:     cfg.Probe.Concurrency,
		MinScoreActive:  cfg.Probe.MinScoreActive,
		MinScorePromote: cfg.Probe.MinScorePromote,
	}, s)

	// ── EPG ────────────────────────────────────────────────────────────────────
	e := epg.New(epg.Config{
		Enabled:         cfg.EPG.Enabled,
		RefreshInterval: cfg.EPG.RefreshInterval,
		LookaheadHours:  cfg.EPG.LookaheadHours,
		CachePath:       cfg.EPG.CachePath,
		ESPNEnabled:     cfg.EPG.ESPNEnabled,
		FootballDataKey: cfg.EPG.FootballDataKey,
	}, s)

	// ── Proxy ──────────────────────────────────────────────────────────────────
	proxyCfg := proxy.Config{
		Enabled:   cfg.Proxy.Enabled,
		Referer:   cfg.Proxy.Referer,
		UserAgent: cfg.Proxy.UserAgent,
	}
	px := proxy.New(proxyCfg, s)

	// ── Discovery ──────────────────────────────────────────────────────────────
	var crawlerList []discovery.Crawler
	if cfg.Discovery.Enabled {
		if len(cfg.Discovery.GistIDs) > 0 {
			crawlerList = append(crawlerList, crawlers.NewGistCrawler(cfg.Discovery.GistIDs, ""))
			log.Printf("Discovery: Gist crawler enabled (%d gists)", len(cfg.Discovery.GistIDs))
		}
		if len(cfg.Discovery.Subreddits) > 0 {
			crawlerList = append(crawlerList, crawlers.NewRedditCrawler(cfg.Discovery.Subreddits))
			log.Printf("Discovery: Reddit crawler enabled (%d subreddits)", len(cfg.Discovery.Subreddits))
		}
		if len(cfg.Discovery.TelegramChannels) > 0 {
			crawlerList = append(crawlerList, crawlers.NewTelegramCrawler(cfg.Discovery.TelegramChannels))
			log.Printf("Discovery: Telegram crawler enabled (%d channels)", len(cfg.Discovery.TelegramChannels))
		}
		if len(cfg.Discovery.DirectM3UURLs) > 0 {
			crawlerList = append(crawlerList, crawlers.NewDirectM3UCrawler(cfg.Discovery.DirectM3UURLs))
			log.Printf("Discovery: DirectM3U crawler enabled (%d URLs)", len(cfg.Discovery.DirectM3UURLs))
		}
	}

	matcher := discovery.NewMatcher(s)
	discEngine := discovery.NewEngine(discovery.Config{
		Enabled:          cfg.Discovery.Enabled,
		DefaultInterval:  cfg.Discovery.DefaultInterval,
		PriorityInterval: cfg.Discovery.PriorityInterval,
	}, crawlerList, matcher, v)

	// ── API ────────────────────────────────────────────────────────────────────
	serverAddr := fmt.Sprintf("http://%s", cfg.Server.Addr())
	h := api.New(s, e, px, v, proxyCfg, serverAddr)
	mux := http.NewServeMux()
	h.RegisterRoutes(mux)
	discEngine.RegisterRoutes(mux)

	// ── Background workers ─────────────────────────────────────────────────────
	go s.RunSnapshotter(ctx, cfg.Store.SnapshotInterval)
	go v.RunProber(ctx)
	go e.RunRefresher(ctx)
	go discEngine.Run(ctx)

	// ── Match-aware priority escalation — runs every 15 min ───────────────────
	go func() {
		ticker := time.NewTicker(15 * time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				ids, end := e.PriorityChannelIDs(2 * time.Hour)
				if len(ids) > 0 {
					log.Printf("Priority escalation: %d channels with match in <2h", len(ids))
					discEngine.SetPriorityChannels(ids, end)
				}
			case <-ctx.Done():
				return
			}
		}
	}()

	log.Println("All workers started.")

	// ── HTTP server ────────────────────────────────────────────────────────────
	srv := &http.Server{
		Addr:         cfg.Server.Addr(),
		Handler:      mux,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		log.Println("Shutting down…")
		cancel()
		shutCtx, sc := context.WithTimeout(context.Background(), 10*time.Second)
		defer sc()
		_ = srv.Shutdown(shutCtx)
	}()

	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("server: %v", err)
	}
	log.Println("Stopped.")
}

// // cmd/main.go
// // NativeStream Server — entry point. Wires all Phase 2 components.

// package main

// import (
// 	"context"
// 	"fmt"
// 	"log"
// 	"net/http"
// 	"os"
// 	"os/signal"
// 	"syscall"
// 	"time"

// 	"github.com/fredrick-karuri/nativestream/server/api"
// 	"github.com/fredrick-karuri/nativestream/server/config"
// 	"github.com/fredrick-karuri/nativestream/server/epg"
// 	"github.com/fredrick-karuri/nativestream/server/proxy"
// 	"github.com/fredrick-karuri/nativestream/server/service"
// 	"github.com/fredrick-karuri/nativestream/server/store"
// 	"github.com/fredrick-karuri/nativestream/server/validator"
// )

// func main() {
// 	// ── CLI flags ──────────────────────────────────────────────────────────────
// 	if len(os.Args) > 1 {
// 		switch os.Args[1] {
// 		case "--install-service":
// 			binary := ""
// 			if len(os.Args) > 2 {
// 				binary = os.Args[2]
// 			}
// 			if err := service.Install(binary); err != nil {
// 				log.Fatalf("install-service: %v", err)
// 			}
// 			return
// 		case "--uninstall-service":
// 			if err := service.Uninstall(); err != nil {
// 				log.Fatalf("uninstall-service: %v", err)
// 			}
// 			return
// 		case "--help", "-h":
// 			fmt.Println("NativeStream Server")
// 			fmt.Println("Usage:")
// 			fmt.Println("  nativestream-server                     Start the server")
// 			fmt.Println("  nativestream-server --install-service   Register as launchd service")
// 			fmt.Println("  nativestream-server --uninstall-service Remove launchd service")
// 			return
// 		}
// 	}

// 	// ── Config ─────────────────────────────────────────────────────────────────
// 	cfg, err := config.Load()
// 	if err != nil {
// 		log.Fatalf("config: %v", err)
// 	}

// 	log.Printf("NativeStream Server v2.0")
// 	log.Printf("Listening on http://%s", cfg.Server.Addr())

// 	// ── Context (for graceful shutdown) ───────────────────────────────────────
// 	ctx, cancel := context.WithCancel(context.Background())
// 	defer cancel()

// 	// ── Store ──────────────────────────────────────────────────────────────────
// 	s := store.New(cfg.Store.SnapshotPath)
// 	if err := s.Load(); err != nil {
// 		log.Printf("⚠ store load: %v (starting fresh)", err)
// 	}
// 	log.Printf("Store loaded. Channels: %d", func() int { t, _ := s.Count(); return t }())

// 	// ── Validator ──────────────────────────────────────────────────────────────
// 	valCfg := validator.Config{
// 		Interval:        cfg.Probe.Interval,
// 		Timeout:         cfg.Probe.Timeout,
// 		Concurrency:     cfg.Probe.Concurrency,
// 		MinScoreActive:  cfg.Probe.MinScoreActive,
// 		MinScorePromote: cfg.Probe.MinScorePromote,
// 	}
// 	v := validator.New(valCfg, s)

// 	// ── EPG ────────────────────────────────────────────────────────────────────
// 	epgCfg := epg.Config{
// 		Enabled:         cfg.EPG.Enabled,
// 		RefreshInterval: cfg.EPG.RefreshInterval,
// 		LookaheadHours:  cfg.EPG.LookaheadHours,
// 		CachePath:       cfg.EPG.CachePath,
// 		ESPNEnabled:     cfg.EPG.ESPNEnabled,
// 		FootballDataKey: cfg.EPG.FootballDataKey,
// 	}
// 	e := epg.New(epgCfg, s)

// 	// ── Proxy ──────────────────────────────────────────────────────────────────
// 	proxyCfg := proxy.Config{
// 		Enabled:   cfg.Proxy.Enabled,
// 		Referer:   cfg.Proxy.Referer,
// 		UserAgent: cfg.Proxy.UserAgent,
// 	}
// 	px := proxy.New(proxyCfg, s)

// 	// ── API ────────────────────────────────────────────────────────────────────
// 	serverAddr := fmt.Sprintf("http://%s", cfg.Server.Addr())
// 	h := api.New(s, e, px, v, proxyCfg, serverAddr)

// 	// ── Background workers ─────────────────────────────────────────────────────
// 	go s.RunSnapshotter(ctx, cfg.Store.SnapshotInterval)
// 	go v.RunProber(ctx)
// 	go e.RunRefresher(ctx)

// 	log.Printf("Workers started (snapshotter, prober, EPG refresher)")

// 	// ── HTTP server ────────────────────────────────────────────────────────────
// 	srv := &http.Server{
// 		Addr:         cfg.Server.Addr(),
// 		Handler:      h.Router(),
// 		ReadTimeout:  30 * time.Second,
// 		WriteTimeout: 60 * time.Second,
// 		IdleTimeout:  120 * time.Second,
// 	}

// 	// ── Graceful shutdown ──────────────────────────────────────────────────────
// 	sigCh := make(chan os.Signal, 1)
// 	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

// 	go func() {
// 		<-sigCh
// 		log.Println("Shutting down…")
// 		cancel()
// 		shutCtx, shutCancel := context.WithTimeout(context.Background(), 10*time.Second)
// 		defer shutCancel()
// 		if err := srv.Shutdown(shutCtx); err != nil {
// 			log.Printf("HTTP shutdown: %v", err)
// 		}
// 	}()

// 	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
// 		log.Fatalf("server: %v", err)
// 	}

// 	log.Println("Server stopped. Final snapshot written.")
// }