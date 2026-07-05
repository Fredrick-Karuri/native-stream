// proxy/rewriter.go
package proxy

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"net/url"
	"strings"
)


func isMasterPlaylist(body string) bool {
	return strings.Contains(body, "#EXT-X-STREAM-INF")
}

func (p *Proxy) rewritePlaylist(body, baseURL, channelID string, headers map[string]string) string {
	if isMasterPlaylist(body) {
		return p.rewriteMasterPlaylist(body, baseURL, channelID)
	}
	return p.rewriteMediaPlaylist(body, baseURL, channelID, headers)
}

// rewriteMasterPlaylist rewrites variant playlist URLs to route through the proxy.
// Variant URLs point to child media playlists — not segments — so they must route
// through the main proxy handler, not the segment handler.
func (p *Proxy) rewriteMasterPlaylist(body, baseURL, channelID string) string {
	lines := strings.Split(body, "\n")
	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		// Resolve relative variant URL
		resolved := resolveURL(trimmed, baseURL)
		// Route through proxy as a playlist request via query param
		lines[i] = fmt.Sprintf("/stream/%s/proxy?url=%s", channelID, url.QueryEscape(resolved))
	}
	return strings.Join(lines, "\n")
}

// rewriteMediaPlaylist rewrites segment URLs and URI= attributes to route through
// the segment cache handler with injected headers.
func (p *Proxy) rewriteMediaPlaylist(body, baseURL, channelID string, headers map[string]string) string {
	lines := strings.Split(body, "\n")
	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}
		if strings.HasPrefix(trimmed, "#") && strings.Contains(trimmed, `URI="`) {
			lines[i] = p.proxySecureURIAttr(trimmed, channelID, baseURL, headers)
			continue
		}
		if !strings.HasPrefix(trimmed, "#") {
			lines[i] = p.wrapURLInProxy(trimmed, channelID, baseURL, headers)
		}
	}
	return strings.Join(lines, "\n")
}

func resolveURL(rawURL, baseURL string) string {
	if strings.HasPrefix(rawURL, "http://") || strings.HasPrefix(rawURL, "https://") {
		return rawURL
	}
	base, err := url.Parse(baseURL)
	if err != nil {
		return rawURL
	}
	ref, err := url.Parse(rawURL)
	if err != nil {
		return rawURL
	}
	return base.ResolveReference(ref).String()
}

func (p *Proxy) proxySecureURIAttr(line, channelID, baseURL string, headers map[string]string) string {
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
	proxiedURI := p.wrapURLInProxy(rawURI, channelID, baseURL, headers)
	return line[:start] + proxiedURI + line[start+end:]
}

func (p *Proxy) wrapURLInProxy(targetURL, channelID, baseURL string, headers map[string]string) string {
	if !strings.HasPrefix(targetURL, "http://") && !strings.HasPrefix(targetURL, "https://") {
		if base, err := url.Parse(baseURL); err == nil {
			if ref, err := url.Parse(targetURL); err == nil {
				targetURL = base.ResolveReference(ref).String()
			}
		}
	}

	var lookupKey string
	{
		hash := md5.Sum([]byte(targetURL))
		lookupKey = hex.EncodeToString(hash[:])
	}

	p.cacheSegment(lookupKey, targetURL, headers)
	return fmt.Sprintf("/stream/%s/proxy/seg/%s.ts", channelID, lookupKey)
}
