// discovery/crawlers/gist.go — NS-211
// Fetches M3U content from configured public GitHub Gists.
// Uses If-Modified-Since to skip unchanged gists.

package crawlers

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"

	"github.com/fredrick-karuri/nativestream/server/discovery"
)

type GistCrawler struct {
	gistIDs    []string
	token      string
	client     *http.Client
	mu         sync.Mutex
	lastSeen   map[string]time.Time // gistID → last updated_at
	failCounts map[string]int
}

func NewGistCrawler(gistIDs []string, token string) *GistCrawler {
	return &GistCrawler{
		gistIDs:    gistIDs,
		token:      token,
		client:     &http.Client{Timeout: 15 * time.Second},
		lastSeen:   make(map[string]time.Time),
		failCounts: make(map[string]int),
	}
}

func (c *GistCrawler) Name() string { return "gist" }

func (c *GistCrawler) Fetch(ctx context.Context) ([]discovery.RawItem, error) {
	var (
		mu    sync.Mutex
		items []discovery.RawItem
		wg    sync.WaitGroup
	)

	for _, id := range c.gistIDs {
		wg.Add(1)
		go func(gistID string) {
			defer wg.Done()

			c.mu.Lock()
			suspended := c.failCounts[gistID] >= 5
			c.mu.Unlock()
			if suspended {
				return
			}

			fetched, err := c.fetchGist(ctx, gistID)
			c.mu.Lock()
			if err != nil {
				c.failCounts[gistID]++
				c.mu.Unlock()
				return
			}
			c.failCounts[gistID] = 0
			c.mu.Unlock()

			mu.Lock()
			items = append(items, fetched...)
			mu.Unlock()
		}(id)
	}

	wg.Wait()
	return items, nil
}

type gistResponse struct {
	UpdatedAt string                 `json:"updated_at"`
	Files     map[string]gistFile    `json:"files"`
}

type gistFile struct {
	RawURL   string `json:"raw_url"`
	Language string `json:"language"`
}

func (c *GistCrawler) fetchGist(ctx context.Context, id string) ([]discovery.RawItem, error) {
	url := fmt.Sprintf("https://api.github.com/gists/%s", id)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "NativeStream/1.0")
	req.Header.Set("Accept", "application/vnd.github.v3+json")
	if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}

	// Conditional fetch — skip if not modified
	c.mu.Lock()
	if last, ok := c.lastSeen[id]; ok {
		req.Header.Set("If-Modified-Since", last.UTC().Format(http.TimeFormat))
	}
	c.mu.Unlock()

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	// Respect rate limit
	if remaining := resp.Header.Get("X-RateLimit-Remaining"); remaining == "0" {
		return nil, fmt.Errorf("GitHub rate limit reached")
	}

	if resp.StatusCode == http.StatusNotModified {
		return nil, nil // Nothing changed
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GitHub API %d for gist %s", resp.StatusCode, id)
	}

	var gist gistResponse
	if err := json.NewDecoder(resp.Body).Decode(&gist); err != nil {
		return nil, err
	}

	// Update last seen
	if t, err := time.Parse(time.RFC3339, gist.UpdatedAt); err == nil {
		c.mu.Lock()
		c.lastSeen[id] = t
		c.mu.Unlock()
	}

	// Fetch raw content of each file
	var items []discovery.RawItem
	for _, file := range gist.Files {
		if file.RawURL == "" {
			continue
		}
		content, err := c.fetchRaw(ctx, file.RawURL)
		if err != nil {
			continue
		}
		items = append(items, discovery.RawItem{
			SourceURL: fmt.Sprintf("https://gist.github.com/%s", id),
			Content:   content,
			Timestamp: time.Now(),
		})
	}

	return items, nil
}

func (c *GistCrawler) fetchRaw(ctx context.Context, url string) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", "NativeStream/1.0")

	resp, err := c.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 10<<20)) // max 10MB
	return string(body), err
}