// proxy/proxy.go — NS-141
// Transparent HLS proxy: forwards stream requests with injected headers.
// Rewrites internal .m3u8 playlist URLs to route through the proxy.

package proxy

import (
	"fmt"
	"io"
	"net/http"
	"strings"
	"net/url"

	"github.com/fredrick-karuri/nativestream/server/store"
)

type Config struct {
	Enabled   bool
	Referer   string
	UserAgent string
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
		client: &http.Client{
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				if len(via) >= 5 {
					return fmt.Errorf("too many redirects")
				}
				return nil
			},
		},
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

	// Inject headers
	if p.cfg.Referer != "" {
		req.Header.Set("Referer", p.cfg.Referer)
	}
	ua := p.cfg.UserAgent
	if ua == "" {
		ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
	}
	req.Header.Set("User-Agent", ua)

	// Forward range header if present (important for segment requests)
	if rng := r.Header.Get("Range"); rng != "" {
		req.Header.Set("Range", rng)
	}

	resp, err := p.client.Do(req)
	if err != nil {
		http.Error(w, "upstream error", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	// Copy response headers
	for k, vals := range resp.Header {
		for _, v := range vals {
			w.Header().Add(k, v)
		}
	}
	w.WriteHeader(resp.StatusCode)

	// Stream body — if it's an HLS playlist, rewrite internal URLs
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

func (p *Proxy) rewritePlaylist(body, baseURL, channelID string) string {
    base, err := url.Parse(baseURL)
    if err != nil {
        return body
    }
    lines := strings.Split(body, "\n")
    for i, line := range lines {
        trimmed := strings.TrimSpace(line)
        if trimmed == "" {
            continue
        }
        // Rewrite URI= attributes in tags like #EXT-X-MEDIA
        if strings.HasPrefix(trimmed, "#") && strings.Contains(trimmed, `URI="`) {
            lines[i] = rewriteURIAttr(trimmed, base)
            continue
        }
        // Rewrite stream/segment URLs
        if !strings.HasPrefix(trimmed, "#") {
            ref, err := url.Parse(trimmed)
            if err != nil {
                continue
            }
            lines[i] = base.ResolveReference(ref).String()
        }
    }
    return strings.Join(lines, "\n")
}

func rewriteURIAttr(line string, base *url.URL) string {
    start := strings.Index(line, `URI="`)
    if start == -1 {
        return line
    }
    start += 5
    end := strings.Index(line[start:], `"`)
    if end == -1 {
        return line
    }
    rawURI := line[start : start+end]
    ref, err := url.Parse(rawURI)
    if err != nil {
        return line
    }
    absolute := base.ResolveReference(ref).String()
    return line[:start] + absolute + line[start+end:]
}

// rewritePlaylist rewrites relative and absolute URLs in HLS playlists
// to route through this proxy so AVFoundation doesn't bypass it.

// func (p *Proxy) rewritePlaylist(body, baseURL, channelID string) string {
// 	// For a full implementation, you'd parse each line and rewrite
// 	// relative segment URLs. For M3U8 master playlists with variant
// 	// streams, each variant URL would also be proxied.
// 	// Phase 2 keeps this simple — pass through and let AVFoundation handle segments.
// 	return body
// }