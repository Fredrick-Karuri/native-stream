// proxy/rewriter.go
// Rewrites internal .m3u8 playlist URLs to route through the proxy.
// Handles both segment lines and URI= attributes in HLS tags (e.g. #EXT-X-MEDIA).

package proxy

import (
	"net/url"
	"strings"
)

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