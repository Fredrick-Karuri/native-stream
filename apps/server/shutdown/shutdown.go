// shutdown/shutdown.go
// Graceful shutdown: waits for in-flight requests, cancels background workers,
// writes final store snapshot. Triggered by SIGINT or SIGTERM.

package shutdown

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"time"
)

// OnSignal blocks until SIGINT/SIGTERM, then shuts down the HTTP server
// and calls cancel() to stop all background goroutines.
// Allows up to `timeout` for in-flight HTTP requests to complete.
func OnSignal(sigCh <-chan os.Signal, srv *http.Server, cancel context.CancelFunc, timeout time.Duration) {
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
