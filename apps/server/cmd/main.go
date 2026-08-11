// cmd/main.go

package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/fredrick-karuri/nativestream/server"
	"github.com/fredrick-karuri/nativestream/server/api"
	"github.com/fredrick-karuri/nativestream/server/config"
	"github.com/fredrick-karuri/nativestream/server/control"
	"github.com/fredrick-karuri/nativestream/server/discovery"
	"github.com/fredrick-karuri/nativestream/server/discovery/crawlers"
	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/logging"
	"github.com/fredrick-karuri/nativestream/server/netutil"
	"github.com/fredrick-karuri/nativestream/server/proxy"
	"github.com/fredrick-karuri/nativestream/server/service"
	"github.com/fredrick-karuri/nativestream/server/shutdown"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
	"github.com/grandcat/zeroconf"
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
			fmt.Println("NativeStream Server")
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
	slog.Info("NativeStream Server", "addr", cfg.Server.Addr())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// ── Store ──────────────────────────────────────────────────────────────────
	s := store.New(cfg.Store.SnapshotPath, cfg.Store.MinScoreHealthy)
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
	}, s, cfg.Proxy.Referer, cfg.Proxy.UserAgent, cfg.Proxy.Origin)

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
		Origin:    cfg.Proxy.Origin,
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

	// DirectFetchers — pre-resolved candidates, bypass extractor
	var directFetchers []discovery.DirectFetcher
	if cfg.Discovery.LocalScriptPath != "" {
		lsc := crawlers.NewLocalScriptCrawler(cfg.Discovery.LocalScriptPath)
		directFetchers = append(directFetchers, lsc)
		slog.Info("direct fetcher enabled", "name", "local-script-crawler", "path", cfg.Discovery.LocalScriptPath)
	}

	matcher := discovery.NewMatcher(s)
	discEngine := discovery.NewEngine(discovery.Config{
		Enabled:          cfg.Discovery.Enabled,
		DefaultInterval:  cfg.Discovery.DefaultInterval,
		PriorityInterval: cfg.Discovery.PriorityInterval,
	}, crawlerList, matcher, v)

	discEngine.WithDirectFetchers(directFetchers)

	// ── Control hub ───────────────────────────────────────────────────────────
	hub := control.NewHub()
	go hub.Run(ctx)

	// ── API ────────────────────────────────────────────────────────────────────
	serverAddr := fmt.Sprintf("http://%s:%d", netutil.GetLANIP(), cfg.Server.Port)
	h := api.New(s, e, px, v, proxyCfg, serverAddr, hub, server.Version)

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

	// mDNS advertisement
	mdnsServer, err := zeroconf.Register(
		"NativeStream",
		"_nativestream._tcp",
		"local.",
		cfg.Server.Port,
		[]string{fmt.Sprintf("version=%s", server.Version)},
		nil,
	)
	if err != nil {
		slog.Warn("mDNS registration failed", "err", err)
	} else {
		defer mdnsServer.Shutdown()
		slog.Info("mDNS advertised", "service", "_nativestream._tcp.local")
	}

	ctrlServer, err := zeroconf.Register(
		"NativeStream Control",
		"_nativestream-ctrl._tcp",
		"local.",
		cfg.Server.Port,
		[]string{"version=1", "ws=/ws"},
		nil,
	)
	if err != nil {
		slog.Warn("mDNS control registration failed", "err", err)
	} else {
		defer ctrlServer.Shutdown()
		slog.Info("mDNS control advertised", "service", "_nativestream-ctrl._tcp.local")
	}

	// ── HTTP server ────────────────────────────────────────────────────────────
	srv := &http.Server{
		Addr:         cfg.Server.Addr(),
		Handler:      handler,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	const shutdownTimeout = 10 * time.Second
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	defer signal.Stop(sigCh)
	go shutdown.OnSignal(sigCh, srv, cancel, shutdownTimeout)

	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		slog.Error("server error", "err", err)
		os.Exit(1)
	}
}
