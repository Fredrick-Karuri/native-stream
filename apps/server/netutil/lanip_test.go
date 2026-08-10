// netutil/lanip_test.go
//
// PickLANIP is pure ([]net.Addr -> string), so every case here is a
// hand-built slice of addresses — no real network interface is touched.
// net.Addr is an interface, which is what makes this possible: we can
// construct real *net.IPNet values with whatever IPs we choose, without
// any mocking framework.

package netutil

import (
	"net"
	"testing"
)

func TestPickLANIP_EmptyList_FallsBackToLoopback(t *testing.T) {
	got := PickLANIP([]net.Addr{})

	if got != "127.0.0.1" {
		t.Fatalf("expected fallback 127.0.0.1 for empty input, got %q", got)
	}
}

func TestPickLANIP_LoopbackOnly_FallsBackToLoopback(t *testing.T) {
	addrs := []net.Addr{
		&net.IPNet{IP: net.ParseIP("127.0.0.1")},
	}

	got := PickLANIP(addrs)

	if got != "127.0.0.1" {
		t.Fatalf("expected fallback 127.0.0.1 when only loopback present, got %q", got)
	}
}

func TestPickLANIP_SingleRealIPv4_ReturnsThatAddress(t *testing.T) {
	addrs := []net.Addr{
		&net.IPNet{IP: net.ParseIP("192.168.1.42")},
	}

	got := PickLANIP(addrs)

	if got != "192.168.1.42" {
		t.Fatalf("expected 192.168.1.42, got %q", got)
	}
}

func TestPickLANIP_MixedIPv4First_ReturnsIPv4(t *testing.T) {
	// IPv4 entry appears before the IPv6 entry in the slice.
	addrs := []net.Addr{
		&net.IPNet{IP: net.ParseIP("192.168.1.42")},
		&net.IPNet{IP: net.ParseIP("fe80::1")},
	}

	got := PickLANIP(addrs)

	if got != "192.168.1.42" {
		t.Fatalf("expected IPv4 192.168.1.42 to win, got %q", got)
	}
}

func TestPickLANIP_MixedIPv6First_StillReturnsIPv4(t *testing.T) {
	// IPv6 entry appears BEFORE the IPv4 entry. This pins down that the
	// loop's behavior does not depend on ordering: an IPv6-only entry is
	// skipped (To4() is nil), and scanning continues to the next address
	// rather than stopping or returning early.
	addrs := []net.Addr{
		&net.IPNet{IP: net.ParseIP("fe80::1")},
		&net.IPNet{IP: net.ParseIP("192.168.1.42")},
	}

	got := PickLANIP(addrs)

	if got != "192.168.1.42" {
		t.Fatalf("expected IPv4 192.168.1.42 to win regardless of position, got %q", got)
	}
}

func TestPickLANIP_MalformedEntry_SkippedWithoutPanic(t *testing.T) {
	// net.Addr is an interface — nothing guarantees every implementation
	// is *net.IPNet. A real non-*net.IPNet implementation (net.UnixAddr
	// satisfies net.Addr's two methods trivially) proves the type
	// assertion's ok-check protects against a panic, and that scanning
	// continues past it to find the real address afterward.
	addrs := []net.Addr{
		&net.UnixAddr{Name: "/tmp/not-an-ip", Net: "unix"},
		&net.IPNet{IP: net.ParseIP("192.168.1.42")},
	}

	got := PickLANIP(addrs)

	if got != "192.168.1.42" {
		t.Fatalf("expected malformed entry to be skipped and 192.168.1.42 returned, got %q", got)
	}
}
