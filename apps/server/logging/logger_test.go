/*
logging/logger_test.go

Tests for Init. Init has no return value and no injectable dependency —
it mutates the process-wide slog default — so these tests verify effect,
not output: (1) the resulting handler's Enabled() decision matches the
requested level, and (2) captured stdout has the expected shape (JSON
object vs plain text line) for the json flag.

Every test restores slog.Default() afterward via t.Cleanup, since Init's
global mutation would otherwise leak between tests and make results
depend on run order.
*/
package logging

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"os"
	"strings"
	"testing"
)

// withRestoredDefaultLogger saves the current slog default and registers
// a cleanup to restore it, isolating a test's call to Init from any test
// that runs after it.
func withRestoredDefaultLogger(t *testing.T) {
	t.Helper()
	previous := slog.Default()
	t.Cleanup(func() {
		slog.SetDefault(previous)
	})
}

// captureStdout redirects os.Stdout for the duration of fn and returns
// everything written to it. Init writes its handler directly to
// os.Stdout with no injection point, so this is the only way to observe
// the actual bytes a real log call would produce.
func captureStdout(t *testing.T, fn func()) string {
	t.Helper()

	original := os.Stdout
	reader, writer, err := os.Pipe()
	if err != nil {
		t.Fatalf("os.Pipe failed: %v", err)
	}
	os.Stdout = writer

	fn()

	if err := writer.Close(); err != nil {
		t.Fatalf("closing pipe writer failed: %v", err)
	}
	os.Stdout = original

	var buf bytes.Buffer
	if _, err := buf.ReadFrom(reader); err != nil {
		t.Fatalf("reading captured stdout failed: %v", err)
	}
	return buf.String()
}

func TestInit_LevelFiltering(t *testing.T) {
	tests := []struct {
		name               string
		level              string
		debugRecordEnabled bool
		infoRecordEnabled  bool
		warnRecordEnabled  bool
	}{
		{"debug enables everything", "debug", true, true, true},
		{"info filters debug only", "info", false, true, true},
		{"warn filters debug and info", "warn", false, false, true},
		{"error filters everything but error", "error", false, false, false},
		{"unrecognized level defaults to info", "not-a-real-level", false, true, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			withRestoredDefaultLogger(t)

			Init(tt.level, true)
			handler := slog.Default().Handler()
			ctx := context.Background()

			if got := handler.Enabled(ctx, slog.LevelDebug); got != tt.debugRecordEnabled {
				t.Errorf("Enabled(Debug) = %v, want %v", got, tt.debugRecordEnabled)
			}
			if got := handler.Enabled(ctx, slog.LevelInfo); got != tt.infoRecordEnabled {
				t.Errorf("Enabled(Info) = %v, want %v", got, tt.infoRecordEnabled)
			}
			if got := handler.Enabled(ctx, slog.LevelWarn); got != tt.warnRecordEnabled {
				t.Errorf("Enabled(Warn) = %v, want %v", got, tt.warnRecordEnabled)
			}
		})
	}
}

func TestInit_JSONTrueProducesJSONOutput(t *testing.T) {
	withRestoredDefaultLogger(t)

	const testMessage = "json handler smoke test"
	output := captureStdout(t, func() {
		Init("info", true)
		slog.Info(testMessage)
	})

	var decoded map[string]any
	if err := json.Unmarshal([]byte(output), &decoded); err != nil {
		t.Fatalf("output is not valid JSON: %v; got: %s", err, output)
	}
	if decoded["msg"] != testMessage {
		t.Errorf("decoded msg = %v, want %q", decoded["msg"], testMessage)
	}
}

func TestInit_JSONFalseProducesTextOutput(t *testing.T) {
	withRestoredDefaultLogger(t)

	const testMessage = "text handler smoke test"
	output := captureStdout(t, func() {
		Init("info", false)
		slog.Info(testMessage)
	})

	// slog.TextHandler emits key=value pairs, not a JSON object — a
	// leading "{" would indicate the wrong handler got wired up.
	if strings.HasPrefix(strings.TrimSpace(output), "{") {
		t.Errorf("expected text-formatted output, got what looks like JSON: %s", output)
	}
	if !strings.Contains(output, `msg="`+testMessage+`"`) {
		t.Errorf("output missing expected msg=%q key/value pair; got: %s", testMessage, output)
	}
}

func TestInit_RespectsLevelWhenWritingRecords(t *testing.T) {
	withRestoredDefaultLogger(t)

	output := captureStdout(t, func() {
		Init("warn", true)
		slog.Info("should be filtered out")
		slog.Warn("should appear")
	})

	if strings.Contains(output, "should be filtered out") {
		t.Errorf("info record was written despite warn-level config; got: %s", output)
	}
	if !strings.Contains(output, "should appear") {
		t.Errorf("warn record missing from output; got: %s", output)
	}
}
