// logging/logger.go — NS-302
// Structured logging setup using stdlib log/slog (Go 1.21+).

package logging

import (
	"log/slog"
	"os"
)

// Init configures the global slog logger.
// level: "debug" | "info" | "warn" | "error"
// json: true for JSON output (production), false for text (development)
func Init(level string, json bool) {
	var l slog.Level
	switch level {
	case "debug":
		l = slog.LevelDebug
	case "warn":
		l = slog.LevelWarn
	case "error":
		l = slog.LevelError
	default:
		l = slog.LevelInfo
	}

	opts := &slog.HandlerOptions{Level: l}
	var handler slog.Handler
	if json {
		handler = slog.NewJSONHandler(os.Stdout, opts)
	} else {
		handler = slog.NewTextHandler(os.Stdout, opts)
	}
	slog.SetDefault(slog.New(handler))
}