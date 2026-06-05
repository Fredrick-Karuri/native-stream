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

// InjectFromMap sets per-link headers onto req, overriding static config
// values where both define the same key (e.g. per-stream User-Agent).
func InjectFromMap(req *http.Request, headers map[string]string) {
	for k, v := range headers {
		req.Header.Set(k, v)
	}
}