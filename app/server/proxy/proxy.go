// proxy/proxy.go — NS-141
// Transparent HLS proxy: forwards stream requests with injected headers.

package proxy

import (
	"io"
	"net/http"
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
		cfg:   cfg,
		store: s,
		client: newClient(),
	}
}

// ServeHTTP handles GET /stream/:id/proxy
func (p *Proxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Extract channel ID from path: /stream/{id}/proxy
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
	w.WriteHeader(resp.StatusCode)

	contentType := resp.Header.Get("Content-Type")
	if strings.Contains(contentType, "mpegurl") || strings.HasSuffix(targetURL, ".m3u8") {
		body, _ := io.ReadAll(resp.Body)
		rewritten := p.rewritePlaylist(string(body), targetURL, channelID)
		w.Write([]byte(rewritten))
	} else {
		// Binary segment — pipe directly, no buffering
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