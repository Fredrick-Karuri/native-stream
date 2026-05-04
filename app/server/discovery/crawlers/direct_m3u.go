// discovery/crawlers/direct_m3u.go — NS-214
// Fetches stable M3U playlist URLs and returns their full content for extraction.
// Uses ETag/Last-Modified for conditional fetches.

package crawlers

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"

	"github.com/fredrick-karuri/nativestream/server/discovery"
)

type DirectM3UCrawler struct {
	urls       []string
	client     *http.Client
	mu         sync.Mutex
	etags      map[string]string
	lastMod    map[string]string
	failCounts map[string]int
}

func NewDirectM3UCrawler(urls []string) *DirectM3UCrawler {
	return &DirectM3UCrawler{
		urls:       urls,
		client:     &http.Client{Timeout: 30 * time.Second},
		etags:      make(map[string]string),
		lastMod:    make(map[string]string),
		failCounts: make(map[string]int),
	}
}

func (c *DirectM3UCrawler) Name() string { return "direct_m3u" }

func (c *DirectM3UCrawler) Fetch(ctx context.Context) ([]discovery.RawItem, error) {
	var (
		mu    sync.Mutex
		items []discovery.RawItem
		wg    sync.WaitGroup
	)

	for _, url := range c.urls {
		wg.Add(1)
		go func(u string) {
			defer wg.Done()

			c.mu.Lock()
			suspended := c.failCounts[u] >= 5
			c.mu.Unlock()
			if suspended {
				return
			}

			item, err := c.fetchURL(ctx, u)
			c.mu.Lock()
			if err != nil {
				c.failCounts[u]++
				c.mu.Unlock()
				return
			}
			c.failCounts[u] = 0
			c.mu.Unlock()

			if item != nil {
				mu.Lock()
				items = append(items, *item)
				mu.Unlock()
			}
		}(url)
	}

	wg.Wait()
	return items, nil
}

func (c *DirectM3UCrawler) fetchURL(ctx context.Context, url string) (*discovery.RawItem, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "NativeStream/1.0")

	// Conditional fetch headers
	c.mu.Lock()
	if etag := c.etags[url]; etag != "" {
		req.Header.Set("If-None-Match", etag)
	}
	if lm := c.lastMod[url]; lm != "" {
		req.Header.Set("If-Modified-Since", lm)
	}
	c.mu.Unlock()

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotModified {
		return nil, nil // Nothing changed
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("direct_m3u %d for %s", resp.StatusCode, url)
	}

	// Cache validation headers for next fetch
	c.mu.Lock()
	if etag := resp.Header.Get("ETag"); etag != "" {
		c.etags[url] = etag
	}
	if lm := resp.Header.Get("Last-Modified"); lm != "" {
		c.lastMod[url] = lm
	}
	c.mu.Unlock()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 20<<20)) // max 20MB
	if err != nil {
		return nil, err
	}

	return &discovery.RawItem{
		SourceURL: url,
		Content:   string(body),
		Timestamp: time.Now(),
	}, nil
}