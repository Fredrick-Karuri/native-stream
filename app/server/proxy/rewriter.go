// proxy/rewriter.go
package proxy

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"net/url"
	"strings"
)

func (p *Proxy) rewritePlaylist(body, baseURL, channelID string) string {
	lines := strings.Split(body, "\n")
	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}

		// 1. Intercept pre-signed AWS URLs inside complex HLS metadata tags
		if strings.HasPrefix(trimmed, "#") && strings.Contains(trimmed, `URI="`) {
			lines[i] = p.proxySecureURIAttr(trimmed, channelID, baseURL)
			continue
		}

		// 2. Catch standard plain segment lines if they appear
		if !strings.HasPrefix(trimmed, "#") {
			lines[i] = p.wrapURLInProxy(trimmed, channelID, baseURL)
		}
	}

	return strings.Join(lines, "\n")
}

func (p *Proxy) proxySecureURIAttr(line string, channelID, baseURL string) string {
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
	proxiedURI := p.wrapURLInProxy(rawURI, channelID, baseURL)
	
	return line[:start] + proxiedURI + line[start+end:]
}

func (p *Proxy) wrapURLInProxy(targetURL string, channelID, baseURL string) string {
	// Resolve relative paths into absolute URLs first
	if !strings.HasPrefix(targetURL, "http://") && !strings.HasPrefix(targetURL, "https://") {
		if base, err := url.Parse(baseURL); err == nil {
			if ref, err := url.Parse(targetURL); err == nil {
				targetURL = base.ResolveReference(ref).String()
			}
		}
	}

	// 🟢 DETERMINISTIC FIXED INDEX: Hash only the clean URL path to extract a stable unique ID
	u, err := url.Parse(targetURL)
	var lookupKey string
	if err == nil {
		hash := md5.Sum([]byte(u.Path))
		lookupKey = hex.EncodeToString(hash[:])
	} else {
		hash := md5.Sum([]byte(targetURL))
		lookupKey = hex.EncodeToString(hash[:])
	}

	// Register the long signed link into our server map using the stable hash key
	p.cacheTargetURL(lookupKey, targetURL)
	
	// Appends a clean, deterministic path token that scales predictably
	return fmt.Sprintf("/stream/%s/proxy/seg/%s.ts", channelID, lookupKey)
}
