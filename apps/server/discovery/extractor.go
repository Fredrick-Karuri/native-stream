// discovery/extractor.go — NS-203
// Extracts M3U8/M3U URLs from raw text content.
// Expands .m3u files by fetching them and extracting individual stream URLs.

package discovery

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"
)

var (
	m3u8Re = regexp.MustCompile(`https?://[^\s"'<>]+\.m3u8(?:\?[^\s"'<>]*)?`)
	m3uRe = regexp.MustCompile(`https?://\S+\.m3u(?:[^8\w]|$)`)
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
		fmt.Fprintf(os.Stderr, "[debug] before m3u8 scan content_len=%d\n", len(item.Content))
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

		fmt.Fprintf(os.Stderr, "[debug] after m3u8 scan found=%d\n", len(out))
		// .m3u files — fetch and expand into individual stream URLs
		for _, url := range m3uRe.FindAllString(item.Content, -1) {
			if strings.HasSuffix(strings.Split(url, "?")[0], ".m3u8") {
				continue // already handled above
			}
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

		fmt.Fprintf(os.Stderr, "[debug] after m3u expand, before EXTM3U parse\n")
		// If content looks like an M3U playlist, parse lines directly
		if strings.Contains(item.Content, "#EXTM3U") {
			lines := strings.Split(item.Content, "\n")
			var lastExtinf string
			for _, line := range lines {
				line = strings.TrimSpace(line)
				if strings.HasPrefix(line, "#EXTINF") {
					lastExtinf = line
					continue
				}
				if strings.HasPrefix(line, "http") && !seen[line] {
					seen[line] = true
					out = append(out, CandidateLink{
						URL:         line,
						SourceURL:   item.SourceURL,
						ContextText: lastExtinf,
						Found:       item.Timestamp,
					})
					lastExtinf = ""
				}
			}
		}
		fmt.Fprintf(os.Stderr, "[debug] after EXTM3U parse total=%d\n", len(out))
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
	// Don't bleed back past the nearest preceding #EXTINF
	if i := strings.LastIndex(text[start:idx], "#EXTINF"); i >= 0 {
		start = start + i
	}
	end := idx + len(url) + 60
	if end > len(text) {
		end = len(text)
	}
	// Don't bleed forward into the next entry's #EXTINF
	if i := strings.Index(text[idx:end], "\n#EXTINF"); i >= 0 {
		end = idx + i
	}
	return strings.TrimSpace(text[start:end])
}