// cmd/main.go — Phase 4: hardened with slog, circuit breaker, graceful shutdown.

package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
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
	"github.com/fredrick-karuri/nativestream/server/logging"
	"github.com/fredrick-karuri/nativestream/server/shutdown"
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
				fmt.Fprintf(os.Stderr, "install-service: %v\n", err)
				os.Exit(1)
			}
			return
		case "--uninstall-service":
			if err := service.Uninstall(); err != nil {
				fmt.Fprintf(os.Stderr, "uninstall-service: %v\n", err)
				os.Exit(1)
			}
			return
		case "--help", "-h":
			fmt.Println("NativeStream Server v4.0")
			fmt.Println("  nativestream-server                     Start")
			fmt.Println("  nativestream-server --install-service   Register launchd service")
			fmt.Println("  nativestream-server --uninstall-service Remove launchd service")
			return
		}
	}

	// ── Config & logging ───────────────────────────────────────────────────────
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "config: %v\n", err)
		os.Exit(1)
	}
	logging.Init("info", false)
	slog.Info("NativeStream Server v4.0", "addr", cfg.Server.Addr())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// ── Store ──────────────────────────────────────────────────────────────────
	s := store.New(cfg.Store.SnapshotPath)
	if err := s.Load(); err != nil {
		slog.Warn("store load failed, starting fresh", "err", err)
	}
	total, healthy := s.Count()
	slog.Info("store loaded", "channels", total, "healthy", healthy)

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
	cb := discovery.NewCircuitBreaker(5, time.Hour)
	_ = cb // available for crawler injection in future

	var crawlerList []discovery.Crawler
	if cfg.Discovery.Enabled {
		if len(cfg.Discovery.GistIDs) > 0 {
			crawlerList = append(crawlerList, crawlers.NewGistCrawler(cfg.Discovery.GistIDs, ""))
			slog.Info("crawler enabled", "name", "gist", "sources", len(cfg.Discovery.GistIDs))
		}
		if len(cfg.Discovery.Subreddits) > 0 {
			crawlerList = append(crawlerList, crawlers.NewRedditCrawler(cfg.Discovery.Subreddits))
			slog.Info("crawler enabled", "name", "reddit", "sources", len(cfg.Discovery.Subreddits))
		}
		if len(cfg.Discovery.TelegramChannels) > 0 {
			crawlerList = append(crawlerList, crawlers.NewTelegramCrawler(cfg.Discovery.TelegramChannels))
			slog.Info("crawler enabled", "name", "telegram", "sources", len(cfg.Discovery.TelegramChannels))
		}
		if len(cfg.Discovery.DirectM3UURLs) > 0 {
			crawlerList = append(crawlerList, crawlers.NewDirectM3UCrawler(cfg.Discovery.DirectM3UURLs))
			slog.Info("crawler enabled", "name", "direct_m3u", "sources", len(cfg.Discovery.DirectM3UURLs))
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

	// Apply middleware stack
	handler := api.LoggingMiddleware(api.RecoveryMiddleware(mux))

	// ── Background workers ─────────────────────────────────────────────────────
	go s.RunSnapshotter(ctx, cfg.Store.SnapshotInterval)
	go v.RunProber(ctx)
	go e.RunRefresher(ctx)
	go discEngine.Run(ctx)

	// Priority escalation — check every 15 min
	go func() {
		ticker := time.NewTicker(15 * time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				ids, end := e.PriorityChannelIDs(2 * time.Hour)
				if len(ids) > 0 {
					slog.Info("priority escalation", "channels", len(ids))
					discEngine.SetPriorityChannels(ids, end)
				}
			case <-ctx.Done():
				return
			}
		}
	}()

	slog.Info("all workers started")

	// ── HTTP server ────────────────────────────────────────────────────────────
	srv := &http.Server{
		Addr:         cfg.Server.Addr(),
		Handler:      handler,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go shutdown.OnSignal(srv, cancel, 10*time.Second)

	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		slog.Error("server error", "err", err)
		os.Exit(1)
	}
}