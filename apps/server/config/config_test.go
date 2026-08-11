// config/config_test.go
//
// loadFile does real file I/O, so these tests write real (temp, disposable)
// YAML files with t.TempDir() rather than mocking anything — Go's stdlib
// makes this cheap and safe (auto-cleaned after each test).

package config

import (
	"os"
	"path/filepath"
	"testing"
)

func writeTempConfig(t *testing.T, contents string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := os.WriteFile(path, []byte(contents), 0644); err != nil {
		t.Fatalf("failed to write temp config: %v", err)
	}
	return path
}

func TestLoadFile_MergesOverridesOntoDefaults(t *testing.T) {
	path := writeTempConfig(t, `
server:
  host: "127.0.0.1"
  port: 9999
`)

	cfg, err := loadFile(Defaults(), path)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.Server.Host != "127.0.0.1" {
		t.Errorf("expected host override to apply, got %q", cfg.Server.Host)
	}
	if cfg.Server.Port != 9999 {
		t.Errorf("expected port override to apply, got %d", cfg.Server.Port)
	}
	// Untouched sections should still carry their defaults.
	if cfg.Probe.Concurrency != 20 {
		t.Errorf("expected default concurrency 20 to survive untouched, got %d", cfg.Probe.Concurrency)
	}
}

func TestLoadFile_MissingFile_ReturnsDefaultsUnchanged(t *testing.T) {
	cfg, err := loadFile(Defaults(), filepath.Join(t.TempDir(), "does-not-exist.yaml"))
	if err != nil {
		t.Fatalf("expected no error for missing file, got: %v", err)
	}
	if cfg.Server.Port != 8889 {
		t.Errorf("expected default port 8889 when file is missing, got %d", cfg.Server.Port)
	}
}

// BUG: an explicit zero value in YAML is indistinguishable from "field
// omitted" in applyFloat, so the user's intent to set 0.0 is silently
// discarded and the default (0.3) wins instead.
func TestLoadFile_ExplicitZeroFloat_IsSilentlyOverriddenByDefault(t *testing.T) {
	path := writeTempConfig(t, `
store:
  min_score_healthy: 0.0
`)

	cfg, err := loadFile(Defaults(), path)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// This assertion documents CURRENT (buggy) behavior: it fails once
	// the bug is fixed, at which point flip it to expect 0.0 and delete
	// this comment.
	if cfg.Store.MinScoreHealthy != 0.3 {
		t.Errorf("expected default 0.3 to leak through (known bug), got %v — "+
			"if this now fails, the zero-value bug in applyFloat has been fixed; update this test", cfg.Store.MinScoreHealthy)
	}
}

// BUG: same shape as above, but for applyInt — an explicit 0 (e.g.
// "disable probe concurrency") is indistinguishable from "omitted".
func TestLoadFile_ExplicitZeroInt_IsSilentlyOverriddenByDefault(t *testing.T) {
	path := writeTempConfig(t, `
probe:
  concurrency: 0
`)

	cfg, err := loadFile(Defaults(), path)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.Probe.Concurrency != 20 {
		t.Errorf("expected default 20 to leak through (known bug), got %d — "+
			"if this now fails, the zero-value bug in applyInt has been fixed; update this test", cfg.Probe.Concurrency)
	}
}

func TestLoadFile_MalformedYAML_ReturnsError(t *testing.T) {
	path := writeTempConfig(t, `server: [this is not valid: yaml structure`)

	_, err := loadFile(Defaults(), path)
	if err == nil {
		t.Fatal("expected an error for malformed YAML, got nil")
	}
}

func TestExpandHome_ExpandsTildePrefix(t *testing.T) {
	home, _ := os.UserHomeDir()
	got := expandHome("~/nativestream/data")
	want := filepath.Join(home, "nativestream/data")

	if got != want {
		t.Errorf("expected %q, got %q", want, got)
	}
}

func TestExpandHome_LeavesAbsolutePathUnchanged(t *testing.T) {
	got := expandHome("/var/lib/nativestream")
	if got != "/var/lib/nativestream" {
		t.Errorf("expected absolute path to pass through unchanged, got %q", got)
	}
}
