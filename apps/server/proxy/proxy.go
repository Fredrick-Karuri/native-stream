// proxy/proxy.go
// Transparent HLS proxy: forwards stream requests with injected headers.

package proxy

import (
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/fredrick-karuri/nativestream/server/store"
)

type Config struct {
	Enabled   bool
	Referer   string
	UserAgent string
	Origin    string
}

type Proxy struct {
	cfg          Config
	store        *store.Store
	client       *http.Client
	segmentCache sync.Map
	enabled      atomic.Bool
}

func New(cfg Config, s *store.Store) *Proxy {
	p := &Proxy{
		cfg:    cfg,
		store:  s,
		client: newClient(),
	}
	p.enabled.Store(cfg.Enabled)
	return p
}

func (p *Proxy) SetEnabled(enabled bool) {
	p.enabled.Store(enabled)
}

func (p *Proxy) IsEnabled() bool {
	return p.enabled.Load()
}

func (p *Proxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path

	// INTERCEPT STABLE DETERMINISTIC CHUNKS
	if strings.Contains(path, "/proxy/seg/") {
		startIdx := strings.Index(path, "/proxy/seg/") + 11
		endIdx := strings.LastIndex(path, ".ts")
		if startIdx >= endIdx || startIdx < 11 {
			http.Error(w, "malformed segment signature", http.StatusBadRequest)
			return
		}
		segID := path[startIdx:endIdx]

		val, exists := p.segmentCache.Load(segID)
		if !exists {
			http.Error(w, "segment token expired from proxy reference", http.StatusGone)
			return
		}
		seg := val.(cachedSegment)

		req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, seg.TargetURL, nil)
		if err != nil {
			http.Error(w, "bad target build", http.StatusBadRequest)
			return
		}
		injectHeaders(req, r, p.cfg)
		InjectFromMap(req, seg.Headers)

		resp, err := p.client.Do(req)
		if err != nil {
			http.Error(w, "upstream target server error", http.StatusBadGateway)
			return
		}
		defer func() {
			if cerr := resp.Body.Close(); cerr != nil {
				slog.Debug("proxy: close segment upstream body", "err", cerr)
			}
		}()

		copyResponseHeaders(w, resp)

		w.Header().Set("Content-Type", "video/MP2T")
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")

		if resp.StatusCode == http.StatusPartialContent {
			if cr := resp.Header.Get("Content-Range"); cr != "" {
				w.Header().Set("Content-Range", cr)
			}
		}

		w.WriteHeader(resp.StatusCode)
		if _, err := io.Copy(w, resp.Body); err != nil {
			slog.Debug("proxy: copy segment body failed", "err", err)
		}
		return
	}

	// ── VARIANT PLAYLIST ROUTING (master playlist child requests) ──
	if variantURL := r.URL.Query().Get("url"); variantURL != "" {
		decoded, err := url.QueryUnescape(variantURL)
		if err != nil {
			http.Error(w, "bad variant url", http.StatusBadRequest)
			return
		}
		if err := validateUpstreamURL(r.Context(), decoded); err != nil {
			slog.Warn("proxy: rejected variant url", "err", err)
			http.Error(w, "upstream url not allowed", http.StatusForbidden)
			return
		}

		parts := strings.Split(strings.Trim(path, "/"), "/")
		if len(parts) < 2 {
			http.Error(w, "invalid path", http.StatusBadRequest)
			return
		}
		channelID := parts[1]

		ch := p.store.Get(channelID)
		headers := map[string]string{}
		if ch != nil && ch.ActiveLink != nil {
			headers = ch.ActiveLink.Headers
		}

		// decoded is validated by validateUpstreamURL above (rejects
		// non-http(s) schemes and non-public IP ranges) before this point.
		req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, decoded, nil) // #nosec G704
		if err != nil {
			http.Error(w, "bad variant upstream URL", http.StatusBadGateway)
			return
		}
		injectHeaders(req, r, p.cfg)
		InjectFromMap(req, headers)

		resp, err := p.client.Do(req) // #nosec G704
		if err != nil {
			http.Error(w, "variant upstream error", http.StatusBadGateway)
			return
		}
		defer func() {
			if cerr := resp.Body.Close(); cerr != nil {
				slog.Debug("proxy: close variant upstream body", "err", cerr)
			}
		}()

		copyResponseHeaders(w, resp)
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		w.WriteHeader(resp.StatusCode)

		body, _ := io.ReadAll(resp.Body)
		rewritten := p.rewriteMediaPlaylist(string(body), decoded, channelID, headers)
		if _, err := w.Write([]byte(rewritten)); err != nil { // #nosec G705 -- Content-Type is forced above with nosniff, so this cannot be sniffed/rendered as HTML
			slog.Debug("proxy: write variant playlist failed", "err", err)
		}
		return
	}

	// ── ORIGINAL PLAYLIST ROUTING ──
	parts := strings.Split(strings.Trim(path, "/"), "/")
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

	// targetURL comes from ch.ActiveLink.URL (server-stored channel config,
	// set via admin channel-creation API), not directly from this request.
	// TODO: apply validateUpstreamURL at channel create/update time once
	// the server is publicly hosted, to close this path too.
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, targetURL, nil) // #nosec G704
	if err != nil {
		http.Error(w, "bad upstream URL", http.StatusBadGateway)
		return
	}

	injectHeaders(req, r, p.cfg)
	InjectFromMap(req, ch.ActiveLink.Headers)
	slog.Debug("proxy upstream request", "url", targetURL, "headers", req.Header)

	resp, err := p.client.Do(req) // #nosec G704
	if err != nil {
		http.Error(w, "upstream error", http.StatusBadGateway)
		return
	}
	defer func() {
		if cerr := resp.Body.Close(); cerr != nil {
			slog.Debug("proxy: close playlist upstream body", "err", cerr)
		}
	}()

	copyResponseHeaders(w, resp)

	contentType := resp.Header.Get("Content-Type")
	isPlaylist := strings.Contains(contentType, "mpegurl") || strings.HasSuffix(targetURL, ".m3u8")

	if isPlaylist {
		// Force the content type explicitly rather than trusting whatever
		// the upstream sent (or omitted) — prevents the rewritten body,
		// which embeds upstream-controlled URI text, from being sniffed
		// and rendered as HTML by a browser (gosec G705).
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl; charset=utf-8")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		w.Header().Set("Pragma", "no-cache")
		w.Header().Set("Expires", "0")
	}

	w.WriteHeader(resp.StatusCode)

	if isPlaylist {
		body, _ := io.ReadAll(resp.Body)

		rewritten := p.rewritePlaylist(string(body), targetURL, channelID, ch.ActiveLink.Headers)
		if _, err := w.Write([]byte(rewritten)); err != nil { // #nosec G705 -- Content-Type is forced above with nosniff, so this cannot be sniffed/rendered as HTML
			slog.Debug("proxy: write playlist failed", "err", err)
		}
	} else {
		if _, err := io.Copy(w, resp.Body); err != nil {
			slog.Debug("proxy: copy passthrough body failed", "err", err)
		}
	}
}

func copyResponseHeaders(w http.ResponseWriter, resp *http.Response) {
	for k, vals := range resp.Header {
		if strings.EqualFold(k, "content-length") {
			continue
		}
		for _, v := range vals {
			w.Header().Add(k, v)
		}
	}
}

// Explicitly stores mapping via the deterministic hash key
type cachedSegment struct {
	TargetURL string
	Headers   map[string]string
}

func (p *Proxy) cacheSegment(key, targetURL string, headers map[string]string) {
	p.segmentCache.Store(key, cachedSegment{TargetURL: targetURL, Headers: headers})
	go func(id string) {
		t := time.NewTimer(8 * time.Minute)
		<-t.C
		p.segmentCache.Delete(id)
	}(key)
}
