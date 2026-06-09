// proxy/proxy.go
// Transparent HLS proxy: forwards stream requests with injected headers.

package proxy

import (
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
	"os"
	"fmt"

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
}

func New(cfg Config, s *store.Store) *Proxy {
	return &Proxy{
		cfg:    cfg,
		store:  s,
		client: newClient(),
	}
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
		defer resp.Body.Close()

		copyResponseHeaders(w, resp)
		
		w.Header().Set("Content-Type", "video/MP2T")
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		
		if resp.StatusCode == http.StatusPartialContent {
			if cr := resp.Header.Get("Content-Range"); cr != "" {
				w.Header().Set("Content-Range", cr)
			}
		}
		
		w.WriteHeader(resp.StatusCode)
		io.Copy(w, resp.Body)
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

	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, targetURL, nil)
	if err != nil {
		http.Error(w, "bad upstream URL", http.StatusBadGateway)
		return
	}

	injectHeaders(req, r, p.cfg)
	InjectFromMap(req, ch.ActiveLink.Headers)
	fmt.Fprintf(os.Stderr, "[proxy] url=%s headers=%v\n", targetURL, req.Header)

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

		rewritten := p.rewritePlaylist(string(body), targetURL, channelID, ch.ActiveLink.Headers)
		w.Write([]byte(rewritten))
	} else {
		io.Copy(w, resp.Body)
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

//  Explicitly stores mapping via the deterministic hash key
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
