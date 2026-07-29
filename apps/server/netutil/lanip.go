// netutil/lanip.go
//
// Selects a LAN-facing IPv4 address for advertising the server's URL
// (e.g. via mDNS, or in log output) — something more useful to a client
// on the local network than "127.0.0.1".

package netutil

import "net"

// PickLANIP inspects a list of interface addresses and returns the first
// non-loopback IPv4 address found. Falls back to "127.0.0.1" if none
// exists — e.g. on a machine with no active network interface, or in a
// sandboxed/offline environment.
//
// Accepting []net.Addr rather than calling net.InterfaceAddrs() directly
// keeps this function pure and unit-testable: callers can pass real OS
// data or hand-built fakes.
func PickLANIP(addrs []net.Addr) string {
	const fallbackAddr = "127.0.0.1"

	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok {
			continue
		}
		if ipNet.IP.IsLoopback() {
			continue
		}
		if ipv4 := ipNet.IP.To4(); ipv4 != nil {
			return ipv4.String()
		}
	}

	return fallbackAddr
}

// GetLANIP is the impure entry point: it asks the OS for real interface
// addresses and delegates the selection logic to PickLANIP. This is the
// function cmd/main.go should call; it is not itself unit-tested, since
// its only job is the OS call — all decision logic lives in PickLANIP.
func GetLANIP() string {
	addrs, _ := net.InterfaceAddrs()
	return PickLANIP(addrs)
}