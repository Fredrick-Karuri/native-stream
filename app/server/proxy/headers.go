// proxy/headers.go
// Injects required upstream headers (Referer, User-Agent, Origin, Range).

package proxy

import "net/http"

func injectHeaders(req *http.Request, r *http.Request, cfg Config) {
	if cfg.Referer != "" {
		req.Header.Set("Referer", cfg.Referer)
	}

	ua := cfg.UserAgent
	if ua == "" {
		ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
	}
	req.Header.Set("User-Agent", ua)

	if cfg.Origin != "" {
		req.Header.Set("Origin", cfg.Origin)
	}

	// Forward range header if present (important for segment requests)
	if rng := r.Header.Get("Range"); rng != "" {
		req.Header.Set("Range", rng)
	}
}