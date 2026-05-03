// validator/validator_test.go
package validator

import (
	"testing"
	"time"
)

func TestLatencyScore(t *testing.T) {
	tests := []struct {
		ms   int64
		want float64
	}{
		{200, 1.0},
		{299, 1.0},
		{500, 0.7},
		{799, 0.7},
		{1000, 0.4},
		{1999, 0.4},
		{3000, 0.1},
	}
	for _, tt := range tests {
		got := latencyScore(time.Duration(tt.ms) * time.Millisecond)
		if got != tt.want {
			t.Errorf("latencyScore(%dms) = %v, want %v", tt.ms, got, tt.want)
		}
	}
}

func TestReachabilityScore(t *testing.T) {
	tests := []struct {
		code int
		want float64
	}{
		{200, 1.0},
		{206, 1.0},
		{301, 0.6},
		{302, 0.6},
		{404, 0.0},
		{500, 0.0},
		{0, 0.0},
	}
	for _, tt := range tests {
		got := reachabilityScore(tt.code)
		if got != tt.want {
			t.Errorf("reachabilityScore(%d) = %v, want %v", tt.code, got, tt.want)
		}
	}
}

func TestBitrateScore(t *testing.T) {
	tests := []struct {
		kbps int
		want float64
	}{
		{0, 0.5},    // unknown
		{300, 0.2},  // low
		{800, 0.4},  // sd
		{2000, 0.7}, // good
		{5000, 1.0}, // hd
	}
	for _, tt := range tests {
		got := bitrateScore(tt.kbps)
		if got != tt.want {
			t.Errorf("bitrateScore(%d) = %v, want %v", tt.kbps, got, tt.want)
		}
	}
}

func TestComputeScoreDeadLink(t *testing.T) {
	score := computeScore(0, 5*time.Second, 0)
	if score > 0.3 {
		t.Errorf("dead link should score < 0.3, got %v", score)
	}
}

func TestComputeScoreHealthyLink(t *testing.T) {
	score := computeScore(1.0, 200*time.Millisecond, 4000)
	if score < 0.7 {
		t.Errorf("healthy link should score > 0.7, got %v", score)
	}
}