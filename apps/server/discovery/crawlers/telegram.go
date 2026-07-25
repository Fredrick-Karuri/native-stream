// discovery/crawlers/telegram.go — NS-213
// Fetches messages from public Telegram channels via t.me/s/:channel web preview.
// No API key or account required for public channels.

package crawlers

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/fredrick-karuri/nativestream/server/discovery"
)

var (
	tgMsgRe  = regexp.MustCompile(`(?s)<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>`)
	htmlTagRe = regexp.MustCompile(`<[^>]+>`)
)

type TelegramCrawler struct {
	channels   []string
	client     *http.Client
	mu         sync.Mutex
	failCounts map[string]int
}

func NewTelegramCrawler(channels []string) *TelegramCrawler {
	return &TelegramCrawler{
		channels:   channels,
		client:     &http.Client{Timeout: 15 * time.Second},
		failCounts: make(map[string]int),
	}
}

func (c *TelegramCrawler) Name() string { return "telegram" }

func (c *TelegramCrawler) Fetch(ctx context.Context) ([]discovery.RawItem, error) {
	var (
		mu    sync.Mutex
		items []discovery.RawItem
		wg    sync.WaitGroup
	)

	for _, ch := range c.channels {
		wg.Add(1)
		go func(channel string) {
			defer wg.Done()

			c.mu.Lock()
			suspended := c.failCounts[channel] >= 5
			c.mu.Unlock()
			if suspended {
				return
			}

			fetched, err := c.fetchChannel(ctx, channel)
			c.mu.Lock()
			if err != nil {
				c.failCounts[channel]++
				c.mu.Unlock()
				return
			}
			c.failCounts[channel] = 0
			c.mu.Unlock()

			mu.Lock()
			items = append(items, fetched...)
			mu.Unlock()
		}(ch)
	}

	wg.Wait()
	return items, nil
}

func (c *TelegramCrawler) fetchChannel(ctx context.Context, channel string) ([]discovery.RawItem, error) {
	// Strip leading @ if present
	channel = strings.TrimPrefix(channel, "@")

	url := fmt.Sprintf("https://t.me/s/%s", channel)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("telegram %d for @%s", resp.StatusCode, channel)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return nil, err
	}

	// Extract message text from HTML
	var items []discovery.RawItem
	matches := tgMsgRe.FindAllStringSubmatch(string(body), -1)
	for _, m := range matches {
		if len(m) < 2 {
			continue
		}
		// Strip HTML tags to get plain text
		text := htmlTagRe.ReplaceAllString(m[1], " ")
		text = strings.TrimSpace(text)
		if text == "" {
			continue
		}
		items = append(items, discovery.RawItem{
			SourceURL: url,
			Content:   text,
			Timestamp: time.Now(),
		})
	}

	return items, nil
}