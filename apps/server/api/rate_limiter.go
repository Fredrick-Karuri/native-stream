// api/rate_limiter.go
//
// A minimal fixed-window, per-source-IP rate limiter, purpose-built for
// the pairing endpoints, which are unauthenticated by construction and
// therefore need their own bound against brute-forceor session-exhaustion attempts.
// Not a general-purpose limiter — no token bucket, no burst allowance — deliberately
// the simplest thing that satisfies "a burst of requests from one IP must not be able
// to exhaust codes or create unbounded sessions."

package api

import (
	"net"
	"net/http"
	"sync"
	"time"
)

// ipRateLimiter allows up to maxRequests from a given key within window,
// resetting the count after window elapses. Safe for concurrent use.
type ipRateLimiter struct {
	mu          sync.Mutex
	window      time.Duration
	maxRequests int
	buckets     map[string]*rateLimitBucket
}

type rateLimitBucket struct {
	count       int
	windowStart time.Time
}

// newIPRateLimiter creates a limiter allowing maxRequests per source key
// within each window.
func newIPRateLimiter(maxRequests int, window time.Duration) *ipRateLimiter {
	return &ipRateLimiter{
		window:      window,
		maxRequests: maxRequests,
		buckets:     make(map[string]*rateLimitBucket),
	}
}

// Allow reports whether a request from key is permitted right now, and
// records it if so.
func (l *ipRateLimiter) Allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()

	now := time.Now()
	bucket, ok := l.buckets[key]
	if !ok || now.Sub(bucket.windowStart) >= l.window {
		l.buckets[key] = &rateLimitBucket{count: 1, windowStart: now}
		return true
	}

	if bucket.count >= l.maxRequests {
		return false
	}
	bucket.count++
	return true
}

// clientIP extracts the source IP from a request, preferring
// RemoteAddr's host portion. Does not trust X-Forwarded-For — this
// limiter is intended for direct LAN/hosted exposure, not behind a proxy
// that would need that header; revisit if pairing traffic starts arriving
// through a reverse proxy.
func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}
