// service/service_test.go
//
// Install/Uninstall are NOT covered: they call os.Create against a
// $HOME-derived path and shell out to `launchctl load`/`unload`, which
// isn't available outside macOS and shouldn't run as a side effect of
// `go test` even where it is. See TestInstallUninstall_NotUnitTestable.

package service

import (
	"strings"
	"testing"
)

// ── renderPlist ──────────────────────────────────────────────────────────

func TestRenderPlist_IncludesLabelAndBinaryPath(t *testing.T) {
	got, err := renderPlist("com.example.test", "/usr/local/bin/myserver")
	if err != nil {
		t.Fatalf("renderPlist() error = %v", err)
	}

	if !strings.Contains(got, "<string>com.example.test</string>") {
		t.Errorf("rendered plist missing label, got:\n%s", got)
	}
	if !strings.Contains(got, "<string>/usr/local/bin/myserver</string>") {
		t.Errorf("rendered plist missing binary path, got:\n%s", got)
	}
}

func TestRenderPlist_IsValidXMLPlistShape(t *testing.T) {
	got, err := renderPlist("com.example.test", "/bin/foo")
	if err != nil {
		t.Fatalf("renderPlist() error = %v", err)
	}

	for _, want := range []string{
		`<?xml version="1.0" encoding="UTF-8"?>`,
		"<plist version=\"1.0\">",
		"<key>RunAtLoad</key>",
		"<true/>",
		"<key>KeepAlive</key>",
	} {
		if !strings.Contains(got, want) {
			t.Errorf("rendered plist missing %q, got:\n%s", want, got)
		}
	}
}

func TestRenderPlist_DoesNotEscapeXMLSpecialCharsInBinaryPath(t *testing.T) {
	// plistTemplate is parsed with text/template, not html/template — no
	// auto-escaping happens. A binary path containing &, <, or > would
	// produce structurally invalid plist XML.
	got, err := renderPlist("com.example.test", "/usr/local/bin/my&server")
	if err != nil {
		t.Fatalf("renderPlist() error = %v", err)
	}

	if !strings.Contains(got, "<string>/usr/local/bin/my&server</string>") {
		t.Errorf("expected raw unescaped &, output changed shape:\n%s", got)
	}
}

func TestRenderPlist_DeterministicForSameInputs(t *testing.T) {
	a, err := renderPlist("com.example.test", "/bin/foo")
	if err != nil {
		t.Fatalf("renderPlist() error = %v", err)
	}
	b, err := renderPlist("com.example.test", "/bin/foo")
	if err != nil {
		t.Fatalf("renderPlist() error = %v", err)
	}

	if a != b {
		t.Errorf("renderPlist() not deterministic:\nfirst:\n%s\nsecond:\n%s", a, b)
	}
}

// ── plistPathIn ──────────────────────────────────────────────────────────

func TestPlistPathIn_BuildsPathUnderLaunchAgents(t *testing.T) {
	got := plistPathIn("/Users/alice")
	want := "/Users/alice/Library/LaunchAgents/com.nativestream.server.plist"

	if got != want {
		t.Errorf("plistPathIn() = %q, want %q", got, want)
	}
}

func TestPlistPathIn_EmptyHomeProducesRelativePath(t *testing.T) {
	got := plistPathIn("")
	want := "Library/LaunchAgents/com.nativestream.server.plist"

	if got != want {
		t.Errorf("plistPathIn(\"\") = %q, want %q", got, want)
	}
}

// ── Install/Uninstall: documented as not unit-testable in current form ────

// TestInstallUninstall_NotUnitTestable is not a real test — it documents
// a boundary.
//
// Both functions do real OS side effects: os.Create/os.Remove against a
// $HOME-derived path, and exec.CommandContext("launchctl", "load"/"unload").
// launchctl doesn't exist off macOS, and even on macOS a unit test
// shouldn't register/deregister a real LaunchAgent as a side effect.
//
// What's already testable is factored out (renderPlist, plistPathIn,
// tested above). Closing this last gap would need a command-runner seam
// (e.g. an unexported `var runLaunchctl = exec.CommandContext` swapped in
// tests) plus HOME injection into plistPath — worth a ticket if
// Install/Uninstall correctness ever becomes a real risk area, but it's
// deliberately not done here since it would touch production code paths
// (os.Create, exec.Command) purely to satisfy a test, not a real need.
func TestInstallUninstall_NotUnitTestable(t *testing.T) {
	t.Skip("Install/Uninstall do real filesystem + launchctl exec — see comment above this test")
}
