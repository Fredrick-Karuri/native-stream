// validator/validator.go — NS-111, NS-112, NS-113, NS-114
// Link validator: scores stream URLs and promotes/quarantines based on health.

package validator

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/fredrick-karuri/nativestream/server/store"
)

// ── Config ────────────────────────────────────────────────────────────────────

type Config struct {
	Interval        time.Duration
	Timeout         time.Duration
	Concurrency     int
	MinScoreActive  float64
	MinScorePromote float64
}

func DefaultConfig() Config {
	return Config{
		Interval:        10 * time.Minute,
		Timeout:         5 * time.Second,
		Concurrency:     20,
		MinScoreActive:  0.3,
		MinScorePromote: 0.5,
	}
}

// ── Candidate (from discovery) ────────────────────────────────────────────────

type Candidate struct {
	URL       string
	ChannelID string
	SourceURL string
}

// ── Validator ─────────────────────────────────────────────────────────────────

type Validator struct {
	cfg    Config
	store  *store.Store
	queue  chan Candidate
	client *http.Client
	mu     sync.Mutex
	lastProbe time.Time
}

func New(cfg Config, s *store.Store) *Validator {
	return &Validator{
		cfg:   cfg,
		store: s,
		queue: make(chan Candidate, 500),
		client: &http.Client{
			Timeout: cfg.Timeout,
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				if len(via) >= 3 {
					return fmt.Errorf("too many redirects")
				}
				return nil
			},
		},
	}
}

// Submit queues a candidate link for immediate probing.
func (v *Validator) Submit(c Candidate) {
	select {
	case v.queue <- c:
	default:
		// Queue full — drop (non-blocking)
	}
}

// RunProber runs the validator worker pool and periodic probe loop.
func (v *Validator) RunProber(ctx context.Context) {
	// Worker pool — drains the queue
	var wg sync.WaitGroup
	for i := 0; i < v.cfg.Concurrency; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for {
				select {
				case c := <-v.queue:
					v.probeCandidate(c)
				case <-ctx.Done():
					return
				}
			}
		}()
	}

	// Periodic probe of all stored active links
	ticker := time.NewTicker(v.cfg.Interval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			v.probeAll()
		case <-ctx.Done():
			wg.Wait()
			return
		}
	}
}

// TriggerProbeAll triggers an immediate out-of-schedule probe of all links.
func (v *Validator) TriggerProbeAll() {
	go v.probeAll()
}

func (v *Validator) LastProbeTime() time.Time {
	v.mu.Lock()
	defer v.mu.Unlock()
	return v.lastProbe
}

// ── Probing ───────────────────────────────────────────────────────────────────

func (v *Validator) probeAll() {
	channels := v.store.All()
	for _, ch := range channels {
		if ch.ActiveLink != nil {
			go v.probeAndUpdate(ch.ActiveLink)
		}
		for _, c := range ch.Candidates {
			go v.probeAndUpdate(c)
		}
	}
	v.mu.Lock()
	v.lastProbe = time.Now()
	v.mu.Unlock()
}

func (v *Validator) probeCandidate(c Candidate) {
	link := &store.LinkScore{
		URL:          c.URL,
		ChannelID:    c.ChannelID,
		SourceURL:    c.SourceURL,
		DiscoveredAt: time.Now(),
	}
	v.probeAndUpdate(link)
}

func (v *Validator) probeAndUpdate(link *store.LinkScore) {
	scored := v.measure(link.URL)
	scored.ChannelID = link.ChannelID
	scored.SourceURL = link.SourceURL
	scored.DiscoveredAt = link.DiscoveredAt
	scored.URL = link.URL

	// NS-114: self-healing — if score is good enough, try to promote
	if scored.Score >= v.cfg.MinScorePromote {
		v.store.PromoteIfBetter(scored)
	} else {
		v.store.UpdateScore(link.URL, scored)
	}
}

// measure performs the actual HTTP probe and returns a scored LinkScore.
func (v *Validator) measure(url string) *store.LinkScore {
	link := &store.LinkScore{
		URL:         url,
		LastChecked: time.Now(),
	}

	start := time.Now()

	// HEAD request first — cheap, gets status + headers
	req, err := http.NewRequest(http.MethodHead, url, nil)
	if err != nil {
		link.Score = 0
		return link
	}
	req.Header.Set("User-Agent", "NativeStream/1.0")

	resp, err := v.client.Do(req)
	latency := time.Since(start)
	link.LatencyMS = latency.Milliseconds()

	if err != nil {
		link.Score = computeScore(0, latency, 0)
		link.FailCount++
		return link
	}
	defer resp.Body.Close()

	reachability := reachabilityScore(resp.StatusCode)

	// Partial GET for bitrate estimation + HLS format check
	bitrate := 0
	if reachability > 0 {
		bitrate = v.estimateBitrate(url)
	}

	link.EstBitrateKbps = bitrate
	link.Score = computeScore(reachability, latency, bitrate)
	return link
}

func (v *Validator) estimateBitrate(url string) int {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return 0
	}
	req.Header.Set("Range", "bytes=0-10239") // First 10KB
	req.Header.Set("User-Agent", "NativeStream/1.0")

	start := time.Now()
	resp, err := v.client.Do(req)
	if err != nil {
		return 0
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 10240))
	elapsed := time.Since(start)
	if err != nil || elapsed == 0 {
		return 0
	}

	// Check it looks like an HLS playlist
	if !strings.Contains(string(body[:min(len(body), 50)]), "#EXTM3U") {
		// Could be a TS segment — still valid
	}

	// Estimate kbps: bytes / seconds * 8 / 1000
	kbps := int(float64(len(body)) / elapsed.Seconds() * 8 / 1000)
	return kbps
}

// ── Scoring formula (NS-111) ──────────────────────────────────────────────────

func computeScore(reachability float64, latency time.Duration, bitrateKbps int) float64 {
	return latencyScore(latency)*0.4 + reachability*0.4 + bitrateScore(bitrateKbps)*0.2
}

func latencyScore(d time.Duration) float64 {
	ms := d.Milliseconds()
	switch {
	case ms < 300:
		return 1.0
	case ms < 800:
		return 0.7
	case ms < 2000:
		return 0.4
	default:
		return 0.1
	}
}

func reachabilityScore(statusCode int) float64 {
	switch {
	case statusCode == 200 || statusCode == 206:
		return 1.0
	case statusCode >= 300 && statusCode < 400:
		return 0.6
	default:
		return 0.0
	}
}

func bitrateScore(kbps int) float64 {
	switch {
	case kbps == 0:
		return 0.5 // unknown — assume mid
	case kbps > 3000:
		return 1.0
	case kbps > 1000:
		return 0.7
	case kbps > 500:
		return 0.4
	default:
		return 0.2
	}
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}