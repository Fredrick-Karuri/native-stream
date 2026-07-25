// discovery/extractor_test.go
package discovery

import (
	"context"
	"testing"
	"time"
)

func TestExtractM3U8URLs(t *testing.T) {
	e := NewExtractor()
	items := []RawItem{{
		SourceURL: "https://reddit.com/r/soccerstreams",
		Content:   "Watch here: https://stream.example.com/sky1/index.m3u8?token=abc enjoy the match",
		Timestamp: time.Now(),
	}}
	candidates := e.Extract(context.Background(), items)
	if len(candidates) != 1 {
		t.Fatalf("expected 1 candidate, got %d", len(candidates))
	}
	if candidates[0].URL != "https://stream.example.com/sky1/index.m3u8?token=abc" {
		t.Errorf("wrong URL: %s", candidates[0].URL)
	}
	if candidates[0].SourceURL != "https://reddit.com/r/soccerstreams" {
		t.Errorf("wrong source URL: %s", candidates[0].SourceURL)
	}
}

func TestExtractMultipleURLs(t *testing.T) {
	e := NewExtractor()
	items := []RawItem{{
		Content: `
			Sky: https://cdn.example.com/sky1.m3u8
			BT Sport: https://cdn.example.com/btsport.m3u8?token=xyz
		`,
		Timestamp: time.Now(),
	}}
	candidates := e.Extract(context.Background(), items)
	if len(candidates) != 2 {
		t.Fatalf("expected 2 candidates, got %d", len(candidates))
	}
}

func TestDeduplicatesAcrossItems(t *testing.T) {
	e := NewExtractor()
	url := "https://stream.example.com/live.m3u8"
	items := []RawItem{
		{Content: url, Timestamp: time.Now()},
		{Content: "duplicate: " + url, Timestamp: time.Now()},
	}
	candidates := e.Extract(context.Background(), items)
	if len(candidates) != 1 {
		t.Errorf("expected 1 deduplicated candidate, got %d", len(candidates))
	}
}

func TestNoURLsReturnsEmpty(t *testing.T) {
	e := NewExtractor()
	items := []RawItem{{
		Content:   "No links here, just text about football",
		Timestamp: time.Now(),
	}}
	candidates := e.Extract(context.Background(), items)
	if len(candidates) != 0 {
		t.Errorf("expected 0 candidates, got %d", len(candidates))
	}
}

func TestContextAround(t *testing.T) {
	text := "Watch Sky Sports: https://stream.example.com/sky.m3u8 for the match"
	url := "https://stream.example.com/sky.m3u8"
	ctx := contextAround(text, url)
	if ctx == "" {
		t.Error("expected non-empty context")
	}
	if len(ctx) > 200 {
		t.Errorf("context too long: %d chars", len(ctx))
	}
}