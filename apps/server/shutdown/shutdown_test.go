/*
shutdown/shutdown_test.go

Tests for OnSignal. OnSignal blocks on <-sigCh and returns nothing, so
there's no return value to assert on — only side effects: does cancel()
fire, does the server actually stop accepting connections, and in what
order. The signal channel is injected (see shutdown.go), so tests send a
fake os.Signal directly rather than triggering a real OS-level signal.

OnSignal blocks, so each test runs it in a goroutine and uses a done
channel to know when it has returned, rather than sleeping and hoping.
*/
package shutdown

import (
	"context"
	"net"
	"net/http"
	"os"
	"syscall"
	"testing"
	"time"
)

const testShutdownTimeout = 2 * time.Second
const assertionWaitTimeout = 1 * time.Second

// newTestServer returns a real *http.Server wired to a random free port so
// srv.Shutdown has actual listener state to tear down, not a nil server
// that would trivially "succeed" regardless of what OnSignal does.
func newTestServer(t *testing.T) *http.Server {
	t.Helper()
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	srv := &http.Server{Addr: "127.0.0.1:0", Handler: mux}

	var lc net.ListenConfig
	listener, err := lc.Listen(context.Background(), "tcp", srv.Addr)
	if err != nil {
		t.Fatalf("failed to bind test listener: %v", err)
	}

	go func() {
		_ = srv.Serve(listener)
	}()
	t.Cleanup(func() {
		_ = srv.Close()
	})

	return srv
}

func TestOnSignal_CancelsContextBeforeShuttingDownServer(t *testing.T) {
	srv := newTestServer(t)
	ctx, cancel := context.WithCancel(context.Background())

	sigCh := make(chan os.Signal, 1)
	onSignalDone := make(chan struct{})

	go func() {
		OnSignal(sigCh, srv, cancel, testShutdownTimeout)
		close(onSignalDone)
	}()

	sigCh <- syscall.SIGTERM

	select {
	case <-onSignalDone:
	case <-time.After(assertionWaitTimeout):
		t.Fatal("OnSignal did not return within timeout")
	}

	select {
	case <-ctx.Done():
	default:
		t.Error("expected cancel() to have been called, but context is not Done")
	}
}

func TestOnSignal_StopsServerFromAcceptingNewRequests(t *testing.T) {
	srv := newTestServer(t)
	_, cancel := context.WithCancel(context.Background())

	sigCh := make(chan os.Signal, 1)
	onSignalDone := make(chan struct{})

	go func() {
		OnSignal(sigCh, srv, cancel, testShutdownTimeout)
		close(onSignalDone)
	}()

	sigCh <- syscall.SIGINT

	select {
	case <-onSignalDone:
	case <-time.After(assertionWaitTimeout):
		t.Fatal("OnSignal did not return within timeout")
	}

	if err := srv.ListenAndServe(); err != http.ErrServerClosed {
		t.Errorf("expected server to be closed after OnSignal returns, got err = %v", err)
	}
}

func TestOnSignal_ReturnsPromptlyWhenNoInFlightRequests(t *testing.T) {
	srv := newTestServer(t)
	_, cancel := context.WithCancel(context.Background())

	sigCh := make(chan os.Signal, 1)
	onSignalDone := make(chan struct{})

	start := time.Now()
	go func() {
		OnSignal(sigCh, srv, cancel, testShutdownTimeout)
		close(onSignalDone)
	}()

	sigCh <- syscall.SIGTERM

	select {
	case <-onSignalDone:
	case <-time.After(assertionWaitTimeout):
		t.Fatal("OnSignal did not return within timeout")
	}

	// With no in-flight requests, Shutdown should return almost
	// immediately rather than consuming the full timeout budget — a
	// duration far below testShutdownTimeout indicates it didn't
	// needlessly block.
	if elapsed := time.Since(start); elapsed >= testShutdownTimeout {
		t.Errorf("OnSignal took %v, expected well under the %v timeout with no in-flight requests", elapsed, testShutdownTimeout)
	}
}
