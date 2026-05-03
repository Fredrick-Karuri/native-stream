// cmd/main.go
// NativeStream Server — entry point. Wires all Phase 2 components.

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
	"github.com/fredrick-karuri/nativestream/server/epg"
	"github.com/fredrick-karuri/nativestream/server/proxy"
	"github.com/fredrick-karuri/nativestream/server/service"
	"github.com/fredrick-karuri/nativestream/server/store"
	"github.com/fredrick-karuri/nativestream/server/validator"
)

func main() {
	// ── CLI flags ──────────────────────────────────────────────────────────────
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
			fmt.Println("NativeStream Server")
			fmt.Println("Usage:")
			fmt.Println("  nativestream-server                     Start the server")
			fmt.Println("  nativestream-server --install-service   Register as launchd service")
			fmt.Println("  nativestream-server --uninstall-service Remove launchd service")
			return
		}
	}

	// ── Config ─────────────────────────────────────────────────────────────────
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	log.Printf("NativeStream Server v2.0")
	log.Printf("Listening on http://%s", cfg.Server.Addr())

	// ── Context (for graceful shutdown) ───────────────────────────────────────
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// ── Store ──────────────────────────────────────────────────────────────────
	s := store.New(cfg.Store.SnapshotPath)
	if err := s.Load(); err != nil {
		log.Printf("⚠ store load: %v (starting fresh)", err)
	}
	log.Printf("Store loaded. Channels: %d", func() int { t, _ := s.Count(); return t }())

	// ── Validator ──────────────────────────────────────────────────────────────
	valCfg := validator.Config{
		Interval:        cfg.Probe.Interval,
		Timeout:         cfg.Probe.Timeout,
		Concurrency:     cfg.Probe.Concurrency,
		MinScoreActive:  cfg.Probe.MinScoreActive,
		MinScorePromote: cfg.Probe.MinScorePromote,
	}
	v := validator.New(valCfg, s)

	// ── EPG ────────────────────────────────────────────────────────────────────
	epgCfg := epg.Config{
		Enabled:         cfg.EPG.Enabled,
		RefreshInterval: cfg.EPG.RefreshInterval,
		LookaheadHours:  cfg.EPG.LookaheadHours,
		CachePath:       cfg.EPG.CachePath,
		ESPNEnabled:     cfg.EPG.ESPNEnabled,
		FootballDataKey: cfg.EPG.FootballDataKey,
	}
	e := epg.New(epgCfg, s)

	// ── Proxy ──────────────────────────────────────────────────────────────────
	proxyCfg := proxy.Config{
		Enabled:   cfg.Proxy.Enabled,
		Referer:   cfg.Proxy.Referer,
		UserAgent: cfg.Proxy.UserAgent,
	}
	px := proxy.New(proxyCfg, s)

	// ── API ────────────────────────────────────────────────────────────────────
	serverAddr := fmt.Sprintf("http://%s", cfg.Server.Addr())
	h := api.New(s, e, px, v, proxyCfg, serverAddr)

	// ── Background workers ─────────────────────────────────────────────────────
	go s.RunSnapshotter(ctx, cfg.Store.SnapshotInterval)
	go v.RunProber(ctx)
	go e.RunRefresher(ctx)

	log.Printf("Workers started (snapshotter, prober, EPG refresher)")

	// ── HTTP server ────────────────────────────────────────────────────────────
	srv := &http.Server{
		Addr:         cfg.Server.Addr(),
		Handler:      h.Router(),
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	// ── Graceful shutdown ──────────────────────────────────────────────────────
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-sigCh
		log.Println("Shutting down…")
		cancel()
		shutCtx, shutCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer shutCancel()
		if err := srv.Shutdown(shutCtx); err != nil {
			log.Printf("HTTP shutdown: %v", err)
		}
	}()

	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("server: %v", err)
	}

	log.Println("Server stopped. Final snapshot written.")
}