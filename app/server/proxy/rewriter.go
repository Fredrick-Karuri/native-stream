// proxy/rewriter.go
package proxy

import (
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
			lines[i] = p.proxySecureURIAttr(trimmed, channelID)
			continue
		}

		// 2. Catch standard plain segment lines if they appear
		if !strings.HasPrefix(trimmed, "#") {
			lines[i] = p.wrapURLInProxy(trimmed, channelID)
		}
	}

	return strings.Join(lines, "\n")
}

func (p *Proxy) proxySecureURIAttr(line string, channelID string) string {
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
	proxiedURI := p.wrapURLInProxy(rawURI, channelID)
	
	return line[:start] + proxiedURI + line[start+end:]
}

func (p *Proxy) wrapURLInProxy(targetURL string, channelID string) string {
	// Query-escape the absolute AWS link so characters like '?' and '&' don't break Go routing
	escapedTarget := url.QueryEscape(targetURL)
	
	// Route it to your dedicated server sub-endpoint
	return fmt.Sprintf("/stream/%s/proxy/segment?target=%s", channelID, escapedTarget)
}
