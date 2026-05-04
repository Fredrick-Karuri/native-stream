// discovery/crawlers/reddit.go — NS-212
// Fetches new posts + comments from configured subreddits via public JSON API.

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

type RedditCrawler struct {
	subreddits []string
	client     *http.Client
	mu         sync.Mutex
	lastSeen   map[string]string // subreddit → last post fullname (t3_xxx)
	failCounts map[string]int
}

func NewRedditCrawler(subreddits []string) *RedditCrawler {
	return &RedditCrawler{
		subreddits: subreddits,
		client:     &http.Client{Timeout: 15 * time.Second},
		lastSeen:   make(map[string]string),
		failCounts: make(map[string]int),
	}
}

func (c *RedditCrawler) Name() string { return "reddit" }

func (c *RedditCrawler) Fetch(ctx context.Context) ([]discovery.RawItem, error) {
	var (
		mu    sync.Mutex
		items []discovery.RawItem
		wg    sync.WaitGroup
	)

	for _, sub := range c.subreddits {
		wg.Add(1)
		go func(subreddit string) {
			defer wg.Done()

			c.mu.Lock()
			suspended := c.failCounts[subreddit] >= 5
			c.mu.Unlock()
			if suspended {
				return
			}

			// Rate limit: 1 req/sec per subreddit
			time.Sleep(time.Second)

			fetched, err := c.fetchSubreddit(ctx, subreddit)
			c.mu.Lock()
			if err != nil {
				c.failCounts[subreddit]++
				c.mu.Unlock()
				return
			}
			c.failCounts[subreddit] = 0
			c.mu.Unlock()

			mu.Lock()
			items = append(items, fetched...)
			mu.Unlock()
		}(sub)
	}

	wg.Wait()
	return items, nil
}

type redditListing struct {
	Data struct {
		Children []struct {
			Data struct {
				Name     string  `json:"name"`      // fullname e.g. t3_abc123
				Selftext string  `json:"selftext"`
				Title    string  `json:"title"`
				URL      string  `json:"url"`
				Created  float64 `json:"created_utc"`
			} `json:"data"`
		} `json:"children"`
	} `json:"data"`
}

func (c *RedditCrawler) fetchSubreddit(ctx context.Context, sub string) ([]discovery.RawItem, error) {
	c.mu.Lock()
	lastName := c.lastSeen[sub]
	c.mu.Unlock()

	url := fmt.Sprintf("https://www.reddit.com/r/%s/new.json?limit=25", sub)
	if lastName != "" {
		url += "&before=" + lastName
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "NativeStream/1.0 (stream discovery)")

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusTooManyRequests {
		return nil, fmt.Errorf("reddit rate limited")
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("reddit %d for r/%s", resp.StatusCode, sub)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return nil, err
	}

	var listing redditListing
	if err := json.Unmarshal(body, &listing); err != nil {
		return nil, err
	}

	var items []discovery.RawItem
	var newestName string

	for i, child := range listing.Data.Children {
		d := child.Data
		if i == 0 {
			newestName = d.Name
		}
		// Combine title + selftext for link extraction
		content := d.Title + "\n" + d.Selftext + "\n" + d.URL
		t := time.Unix(int64(d.Created), 0)
		items = append(items, discovery.RawItem{
			SourceURL: fmt.Sprintf("https://www.reddit.com/r/%s", sub),
			Content:   content,
			Timestamp: t,
		})
	}

	if newestName != "" {
		c.mu.Lock()
		c.lastSeen[sub] = newestName
		c.mu.Unlock()
	}

	return items, nil
}