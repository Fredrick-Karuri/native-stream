// discovery/extractor.go — NS-203
// Extracts M3U8/M3U URLs from raw text content.
// Expands .m3u files by fetching them and extracting individual stream URLs.

package discovery

import (
	"context"
	"io"
	"net/http"
	"regexp"
	"strings"
	"time"
)

var (
	m3u8Re = regexp.MustCompile(`https?://[^\s"'<>]+\.m3u8(?:\?[^\s"'<>]*)?`)
	m3uRe  = regexp.MustCompile(`https?://[^\s"'<>]+\.m3u(?:\?[^\s"'<>]*)?`)
)

type LinkExtractor struct {
	client *http.Client
}

func NewExtractor() *LinkExtractor {
	return &LinkExtractor{
		client: &http.Client{Timeout: 10 * time.Second},
	}
}

// Extract pulls candidate links from a batch of raw items.
func (e *LinkExtractor) Extract(ctx context.Context, items []RawItem) []CandidateLink {
	seen := map[string]bool{}
	var out []CandidateLink

	for _, item := range items {
		// Direct .m3u8 URLs
		for _, url := range m3u8Re.FindAllString(item.Content, -1) {
			if seen[url] {
				continue
			}
			seen[url] = true
			out = append(out, CandidateLink{
				URL:         url,
				SourceURL:   item.SourceURL,
				ContextText: contextAround(item.Content, url),
				Found:       item.Timestamp,
			})
		}

		// .m3u files — fetch and expand into individual stream URLs
		for _, url := range m3uRe.FindAllString(item.Content, -1) {
			if seen[url] {
				continue
			}
			seen[url] = true
			expanded := e.expandM3U(ctx, url, item.SourceURL)
			for _, c := range expanded {
				if !seen[c.URL] {
					seen[c.URL] = true
					out = append(out, c)
				}
			}
		}
	}

	return out
}

// expandM3U fetches a .m3u file and extracts all stream URLs inside it.
func (e *LinkExtractor) expandM3U(ctx context.Context, m3uURL, sourceURL string) []CandidateLink {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, m3uURL, nil)
	if err != nil {
		return nil
	}
	req.Header.Set("User-Agent", "NativeStream/1.0")

	resp, err := e.client.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 5<<20)) // max 5MB
	if err != nil {
		return nil
	}

	var out []CandidateLink
	lines := strings.Split(string(body), "\n")
	var lastExtinf string

	for _, line := range lines {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "#EXTINF") {
			lastExtinf = line
			continue
		}
		if strings.HasPrefix(line, "http") && strings.Contains(line, ".m3u8") {
			out = append(out, CandidateLink{
				URL:         line,
				SourceURL:   sourceURL,
				ContextText: lastExtinf,
				Found:       time.Now(),
			})
			lastExtinf = ""
		}
	}

	// Also scan body for any .m3u8 URLs not on their own line
	for _, url := range m3u8Re.FindAllString(string(body), -1) {
		already := false
		for _, c := range out {
			if c.URL == url {
				already = true
				break
			}
		}
		if !already {
			out = append(out, CandidateLink{
				URL:       url,
				SourceURL: sourceURL,
				Found:     time.Now(),
			})
		}
	}

	return out
}

// contextAround returns up to 120 chars surrounding a URL in the source text.
func contextAround(text, url string) string {
	idx := strings.Index(text, url)
	if idx < 0 {
		return ""
	}
	start := idx - 60
	if start < 0 {
		start = 0
	}
	end := idx + len(url) + 60
	if end > len(text) {
		end = len(text)
	}
	return strings.TrimSpace(text[start:end])
}