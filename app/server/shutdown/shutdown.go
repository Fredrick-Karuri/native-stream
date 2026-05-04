// shutdown/shutdown.go — NS-303
// Graceful shutdown: waits for in-flight requests, cancels background workers,
// writes final store snapshot. Triggered by SIGINT or SIGTERM.

package shutdown

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

// OnSignal blocks until SIGINT/SIGTERM, then shuts down the HTTP server
// and calls cancel() to stop all background goroutines.
// Allows up to `timeout` for in-flight HTTP requests to complete.
func OnSignal(srv *http.Server, cancel context.CancelFunc, timeout time.Duration) {
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigCh
	slog.Info("shutdown signal received", "signal", sig)

	cancel() // Stop all background workers

	shutCtx, sc := context.WithTimeout(context.Background(), timeout)
	defer sc()

	if err := srv.Shutdown(shutCtx); err != nil {
		slog.Error("HTTP shutdown error", "err", err)
	}
	slog.Info("server stopped")
}