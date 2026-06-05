// proxy/rewriter.go
package proxy

import (
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"net/url"
	"strings"
)

func (p *Proxy) rewritePlaylist(body, baseURL, channelID string, headers map[string]string) string {
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

	u, err := url.Parse(targetURL)
	var lookupKey string
	if err == nil {
		hash := md5.Sum([]byte(u.Path))
		lookupKey = hex.EncodeToString(hash[:])
	} else {
		hash := md5.Sum([]byte(targetURL))
		lookupKey = hex.EncodeToString(hash[:])
	}

	p.cacheSegment(lookupKey, targetURL, headers)
	return fmt.Sprintf("/stream/%s/proxy/seg/%s.ts", channelID, lookupKey)
}
