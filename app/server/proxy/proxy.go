// proxy/proxy.go — NS-141
// Transparent HLS proxy: forwards stream requests with injected headers.

package proxy

import (
	"io"
	"net/http"
	"net/url"
	"strings"

	"github.com/fredrick-karuri/nativestream/server/store"
)

type Config struct {
	Enabled   bool
	Referer   string
	UserAgent string
	Origin    string
}

type Proxy struct {
	cfg    Config
	store  *store.Store
	client *http.Client
}

func New(cfg Config, s *store.Store) *Proxy {
	return &Proxy{
		cfg:    cfg,
		store:  s,
		client: newClient(),
	}
}

// ServeHTTP handles GET /stream/:id/proxy
func (p *Proxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// 🟢 PERMANENT FIXED ENDPOINT: Intercept the query-escaped absolute AWS R2 links
	if strings.Contains(r.URL.Path, "/proxy/segment") {
		rawTarget := r.URL.Query().Get("target")
		decodedTarget, err := url.QueryUnescape(rawTarget)
		if err != nil || decodedTarget == "" {
			http.Error(w, "invalid proxy target payload", http.StatusBadRequest)
			return
		}

		// Create request directly to the raw, unexpired AWS S3 URL extracted from the query
		req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, decodedTarget, nil)
		if err != nil {
			http.Error(w, "bad segment URL translation", http.StatusBadRequest)
			return
		}

		// Injects your mandatory security Referer safely
		injectHeaders(req, r, p.cfg)

		resp, err := p.client.Do(req)
		if err != nil {
			http.Error(w, "upstream media error", http.StatusBadGateway)
			return
		}
		defer resp.Body.Close()

		// Copy upstream metadata headers
		copyResponseHeaders(w, resp)

		// 🟢 OVERRIDE: Force proper stream format container headers
		// Replaces the provider's fake text/plain mask to ensure AVPlayer uses its hardware decoder pipeline
		w.Header().Set("Content-Type", "video/MP2T")
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")

		w.WriteHeader(resp.StatusCode)
		
		// Transparent streaming pipeline with zero structural data parsing overhead
		io.Copy(w, resp.Body)
		return
	}

	// ── ORIGINAL PLAYLIST ROUTING LOGIC (Untouched except path variables) ──
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) < 3 {
		http.Error(w, "invalid path", http.StatusBadRequest)
		return
	}
	channelID := parts[1]

	ch := p.store.Get(channelID)
	if ch == nil || ch.ActiveLink == nil {
		http.Error(w, "channel not found or no active link", http.StatusNotFound)
		return
	}

	targetURL := ch.ActiveLink.URL

	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, targetURL, nil)
	if err != nil {
		http.Error(w, "bad upstream URL", http.StatusBadGateway)
		return
	}

	injectHeaders(req, r, p.cfg)

	resp, err := p.client.Do(req)
	if err != nil {
		http.Error(w, "upstream error", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	copyResponseHeaders(w, resp)
	
	contentType := resp.Header.Get("Content-Type")
	isPlaylist := strings.Contains(contentType, "mpegurl") || strings.HasSuffix(targetURL, ".m3u8")

	if isPlaylist {
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		w.Header().Set("Pragma", "no-cache")
		w.Header().Set("Expires", "0")
	}
	
	w.WriteHeader(resp.StatusCode)

	if isPlaylist {
		body, _ := io.ReadAll(resp.Body)
		rewritten := p.rewritePlaylist(string(body), targetURL, channelID)
		w.Write([]byte(rewritten))
	} else {
		io.Copy(w, resp.Body)
	}
}

func copyResponseHeaders(w http.ResponseWriter, resp *http.Response) {
	for k, vals := range resp.Header {
		for _, v := range vals {
			w.Header().Add(k, v)
		}
	}
}
