package crawlers

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"time"

	"github.com/fredrick-karuri/nativestream/server/discovery"
)

var _ discovery.DirectFetcher = (*LocalScriptCrawler)(nil)

type LocalScriptCrawler struct {
	ScriptPath string
}

func NewLocalScriptCrawler(path string) *LocalScriptCrawler {
	return &LocalScriptCrawler{ScriptPath: path}
}

func (c *LocalScriptCrawler) Name() string { return "local-script-crawler" }

// scriptPayload mirrors the JSON schema printed by the sidecar script.
// Kept private so script schema changes never leak into discovery types.
type scriptPayload struct {
	URL         string            `json:"url"`
	ChannelName string            `json:"channel_name"`
	GroupTitle  string            `json:"group_title"`
	TvgID       string            `json:"tvg_id"`
	LogoURL     string            `json:"logo_url"`
	Headers     map[string]string `json:"headers"`
}

func (c *LocalScriptCrawler) FetchDirect(ctx context.Context) ([]discovery.DirectCandidate, error) {
	// safe absence guard
	if _, err := os.Stat(c.ScriptPath); os.IsNotExist(err) {
		slog.Debug("local-script-crawler: script not found, skipping", "path", c.ScriptPath)
		return nil, nil
	}

	// subprocess with hard deadline
	execCtx, cancel := context.WithTimeout(ctx, 3*time.Minute)
	defer cancel()

	cmd := exec.CommandContext(execCtx, "/bin/bash", c.ScriptPath)
	cmd.Env = os.Environ()
	out, err := cmd.Output()

	if err != nil {
		return nil, fmt.Errorf("local-script-crawler exec: %w", err)
	}

	// unmarshal into private schema, map to DirectCandidate
	var payloads []scriptPayload
	if err := json.Unmarshal(out, &payloads); err != nil {
		return nil, fmt.Errorf("local-script-crawler: failed decoding local scraper payload: %w", err)
	}

	candidates := make([]discovery.DirectCandidate, len(payloads))
	for i, p := range payloads {
		candidates[i] = discovery.DirectCandidate{
			URL:         p.URL,
			ChannelName: p.ChannelName,
			GroupTitle:  p.GroupTitle,
			TvgID:       p.TvgID,
			LogoURL:     p.LogoURL,
			Headers:     p.Headers,
			SourceURL:   c.ScriptPath,
		}
	}

	slog.Debug("local-script-crawler: fetched candidates", "count", len(candidates))
	return candidates, nil
}