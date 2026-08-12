// proxy/ssrf_test.go
//
// ssrf.go is the SSRF guard: it decides whether an upstream URL is safe to
// fetch. Getting this test wrong in either direction is costly — false
// negatives here mean an actual SSRF hole, false positives mean a support
// ticket about legitimate streams being rejected. So the split below is
// deliberate:
//
//   - isPublicIP is pure (net.IP -> bool, no I/O) and gets full coverage
//     across every rejected category named in its doc comment.
//   - validateUpstreamURL calls net.DefaultResolver.LookupIPAddr, a real
//     DNS lookup. Tests here stick to inputs that don't require DNS
//     resolution of a real hostname: parse failures, scheme checks, and
//     literal-IP URLs (http://127.0.0.1/x), which Go's resolver handles
//     without a network round-trip. See TestValidateUpstreamURL_DNSRebinding_NotUnitTestable
//     for what's explicitly out of scope and why.

package proxy

import (
	"context"
	"fmt"
	"net"
	"testing"
)

// ── isPublicIP ───────────────────────────────────────────────────────────

func TestIsPublicIP(t *testing.T) {
	tests := []struct {
		name string
		ip   string
		want bool
	}{
		// Public — should pass
		{"public IPv4 (Google DNS)", "8.8.8.8", true},
		{"public IPv4 (Cloudflare DNS)", "1.1.1.1", true},
		{"public IPv6", "2606:4700:4700::1111", true},

		// Loopback
		{"IPv4 loopback", "127.0.0.1", false},
		{"IPv4 loopback range, not just .1", "127.0.0.53", false},
		{"IPv6 loopback", "::1", false},

		// RFC1918 private
		{"private 10.0.0.0/8", "10.0.0.5", false},
		{"private 172.16.0.0/12", "172.16.0.1", false},
		{"private 172.31.0.0/12 upper bound", "172.31.255.255", false},
		{"private 192.168.0.0/16", "192.168.1.1", false},

		// RFC4193 IPv6 unique local
		{"IPv6 unique local (ULA)", "fc00::1", false},
		{"IPv6 unique local fd00 range", "fd12:3456:789a::1", false},

		// Link-local
		{"IPv4 link-local", "169.254.1.1", false},
		{"cloud metadata address (AWS/GCP/Azure)", "169.254.169.254", false},
		{"AWS ECS task metadata address", "169.254.170.2", false},
		{"IPv6 link-local unicast", "fe80::1", false},

		// Multicast
		{"IPv4 multicast", "224.0.0.1", false},
		{"IPv4 link-local multicast", "224.0.0.251", false}, // mDNS
		{"IPv6 multicast", "ff02::1", false},

		// Unspecified
		{"IPv4 unspecified", "0.0.0.0", false},
		{"IPv6 unspecified", "::", false},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			ip := net.ParseIP(tc.ip)
			if ip == nil {
				t.Fatalf("test setup error: net.ParseIP(%q) returned nil", tc.ip)
			}
			if got := isPublicIP(ip); got != tc.want {
				t.Errorf("isPublicIP(%q) = %v, want %v", tc.ip, got, tc.want)
			}
		})
	}
}

// ── validateUpstreamURL: parsing / scheme / host checks (no DNS needed) ───

func TestValidateUpstreamURL_RejectsMalformedURL(t *testing.T) {
	err := validateUpstreamURL(context.Background(), "://not a url")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want error for malformed URL")
	}
}

func TestValidateUpstreamURL_RejectsNonHTTPScheme(t *testing.T) {
	tests := []string{
		"ftp://example.com/x",
		"file:///etc/passwd",
		"javascript:alert(1)",
		"gopher://example.com",
		"",
	}

	for _, raw := range tests {
		t.Run(raw, func(t *testing.T) {
			var target string
			if raw == "" {
				target = "example.com/x" // no scheme at all
			} else {
				target = raw
			}
			err := validateUpstreamURL(context.Background(), target)
			if err == nil {
				t.Errorf("validateUpstreamURL(%q) = nil, want scheme-rejection error", target)
			}
		})
	}
}

func TestValidateUpstreamURL_AcceptsHTTPAndHTTPSSchemes(t *testing.T) {
	// Scheme check happens before DNS resolution, but a scheme-valid URL
	// with a literal public IP host lets us exercise past the scheme
	// check without a real DNS lookup.
	for _, scheme := range []string{"http", "https"} {
		t.Run(scheme, func(t *testing.T) {
			err := validateUpstreamURL(context.Background(), scheme+"://8.8.8.8/playlist.m3u8")
			if err != nil {
				t.Errorf("validateUpstreamURL(%s scheme, public literal IP) = %v, want nil", scheme, err)
			}
		})
	}
}

func TestValidateUpstreamURL_RejectsMissingHost(t *testing.T) {
	err := validateUpstreamURL(context.Background(), "http:///path-with-no-host")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want error for missing host")
	}
}

// ── validateUpstreamURL: literal-IP hosts (resolver handles these without
//    a real network round-trip, since there's no hostname to look up) ────

func TestValidateUpstreamURL_RejectsLiteralLoopbackIP(t *testing.T) {
	err := validateUpstreamURL(context.Background(), "http://127.0.0.1/internal-api")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want rejection for loopback literal IP")
	}
}

func TestValidateUpstreamURL_RejectsLiteralPrivateIP(t *testing.T) {
	err := validateUpstreamURL(context.Background(), "http://10.0.0.5/internal-api")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want rejection for private literal IP")
	}
}

func TestValidateUpstreamURL_RejectsLiteralCloudMetadataIP(t *testing.T) {
	// The specific address this whole guard exists to stop: cloud
	// metadata endpoints reachable at a fixed link-local address.
	err := validateUpstreamURL(context.Background(), "http://169.254.169.254/latest/meta-data/")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want rejection for cloud metadata IP")
	}
}

func TestValidateUpstreamURL_AcceptsLiteralPublicIP(t *testing.T) {
	err := validateUpstreamURL(context.Background(), "http://8.8.8.8/playlist.m3u8")
	if err != nil {
		t.Errorf("validateUpstreamURL(public literal IP) = %v, want nil", err)
	}
}

func TestValidateUpstreamURL_RejectsLiteralIPv6ULA(t *testing.T) {
	err := validateUpstreamURL(context.Background(), "http://[fc00::1]/x")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want rejection for IPv6 unique-local literal")
	}
}

// ── Documented gap: real-hostname DNS resolution / rebinding ──────────────

// ── validateUpstreamURL: fake-resolver cases (via the lookupIPAddr seam) ──
//
// lookupIPAddr is a package-level var defaulting to the real resolver.
// These tests swap it for a fake for the duration of the test (restored
// via t.Cleanup) to exercise cases a literal-IP URL can't reach: multiple
// A/AAAA records for one hostname (the DNS-rebinding claim in
// validateUpstreamURL's doc comment), and a named hostname that resolves
// to a public address — the happy path other packages' tests rely on to
// prove the guard doesn't just reject everything.

func withFakeResolver(t *testing.T, fn func(ctx context.Context, host string) ([]net.IPAddr, error)) {
	t.Helper()
	original := lookupIPAddr
	lookupIPAddr = fn
	t.Cleanup(func() { lookupIPAddr = original })
}

func TestValidateUpstreamURL_RejectsWhenAnyResolvedAddressIsPrivate(t *testing.T) {
	// This is the actual DNS-rebinding shape: a hostname that resolves to
	// a mix of addresses. validateUpstreamURL must reject if even one
	// resolved address is non-public, not just the first one checked.
	withFakeResolver(t, func(_ context.Context, host string) ([]net.IPAddr, error) {
		if host != "rebinding.test" {
			t.Fatalf("unexpected host passed to resolver: %q", host)
		}
		return []net.IPAddr{
			{IP: net.ParseIP("8.8.8.8")},    // public
			{IP: net.ParseIP("10.0.0.1")},   // private — should trigger rejection
			{IP: net.ParseIP("1.1.1.1")},    // public
		}, nil
	})

	err := validateUpstreamURL(context.Background(), "http://rebinding.test/playlist.m3u8")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want rejection when any resolved address is private")
	}
}

func TestValidateUpstreamURL_AcceptsHostnameResolvingToAllPublicAddresses(t *testing.T) {
	withFakeResolver(t, func(_ context.Context, host string) ([]net.IPAddr, error) {
		return []net.IPAddr{
			{IP: net.ParseIP("8.8.8.8")},
			{IP: net.ParseIP("8.8.4.4")},
		}, nil
	})

	err := validateUpstreamURL(context.Background(), "http://fake-public.test/playlist.m3u8")
	if err != nil {
		t.Errorf("validateUpstreamURL() = %v, want nil when every resolved address is public", err)
	}
}

func TestValidateUpstreamURL_PropagatesResolverError(t *testing.T) {
	withFakeResolver(t, func(_ context.Context, host string) ([]net.IPAddr, error) {
		return nil, fmt.Errorf("simulated DNS failure")
	})

	err := validateUpstreamURL(context.Background(), "http://unresolvable.test/playlist.m3u8")
	if err == nil {
		t.Fatal("validateUpstreamURL() = nil, want error propagated from resolver failure")
	}
}