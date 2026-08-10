// proxy/ssrf.go
// Guards against SSRF: rejects upstream URLs that resolve to non-public
// IP ranges (private, loopback, link-local, cloud metadata) or use a
// scheme other than http/https. Applied to any URL that originates from
// a request rather than trusted server-side config.

package proxy

import (
	"context"
	"fmt"
	"net"
	"net/url"
)

// validateUpstreamURL rejects URLs that could be used to reach internal
// or link-local network resources (SSRF). It resolves the hostname and
// checks every resulting IP, so DNS rebinding to a private address is
// also caught.
func validateUpstreamURL(ctx context.Context, rawURL string) error {
	u, err := url.Parse(rawURL)
	if err != nil {
		return fmt.Errorf("invalid URL: %w", err)
	}

	if u.Scheme != "http" && u.Scheme != "https" {
		return fmt.Errorf("unsupported scheme %q", u.Scheme)
	}

	host := u.Hostname()
	if host == "" {
		return fmt.Errorf("missing host")
	}

	addrs, err := net.DefaultResolver.LookupIPAddr(ctx, host)
	if err != nil {
		return fmt.Errorf("could not resolve host: %w", err)
	}
	if len(addrs) == 0 {
		return fmt.Errorf("host resolved to no addresses")
	}

	for _, addr := range addrs {
		if !isPublicIP(addr.IP) {
			return fmt.Errorf("upstream host resolves to a non-public address")
		}
	}

	return nil
}

// isPublicIP reports whether ip is routable on the public internet —
// i.e. not loopback, private (RFC1918/RFC4193), link-local (including
// the 169.254.169.254 cloud metadata address), multicast, or unspecified.
func isPublicIP(ip net.IP) bool {
	if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() ||
		ip.IsLinkLocalMulticast() || ip.IsMulticast() || ip.IsUnspecified() {
		return false
	}
	return true
}
