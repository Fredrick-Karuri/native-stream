/*
intsafe/intsafe_test.go

Tests for the saturating int conversions. Both functions share the same
three-zone shape (below range, in range, above range) so each gets one
table-driven test covering: exact boundary values, values just past the
boundary, zero, and a comfortably in-range value.
*/
package intsafe

import (
	"math"
	"testing"
)

func TestToInt32(t *testing.T) {
	tests := []struct {
		name string
		in   int
		want int32
	}{
		{"zero", 0, 0},
		{"in range positive", 1_000, 1_000},
		{"in range negative", -1_000, -1_000},
		{"exact max boundary", math.MaxInt32, math.MaxInt32},
		{"exact min boundary", math.MinInt32, math.MinInt32},
		{"one past max saturates", math.MaxInt32 + 1, math.MaxInt32},
		{"one past min saturates", math.MinInt32 - 1, math.MinInt32},
		{"far past max saturates", math.MaxInt32 * 1_000, math.MaxInt32},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := ToInt32(tt.in)
			if got != tt.want {
				t.Errorf("ToInt32(%d) = %d, want %d", tt.in, got, tt.want)
			}
		})
	}
}

func TestFromInt64ToInt32(t *testing.T) {
	tests := []struct {
		name string
		in   int64
		want int32
	}{
		{"zero", 0, 0},
		{"in range positive", 1_000, 1_000},
		{"in range negative", -1_000, -1_000},
		{"exact max boundary", math.MaxInt32, math.MaxInt32},
		{"exact min boundary", math.MinInt32, math.MinInt32},
		{"one past max saturates", int64(math.MaxInt32) + 1, math.MaxInt32},
		{"one past min saturates", int64(math.MinInt32) - 1, math.MinInt32},
		{"max int64 saturates", math.MaxInt64, math.MaxInt32},
		{"min int64 saturates", math.MinInt64, math.MinInt32},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := FromInt64ToInt32(tt.in)
			if got != tt.want {
				t.Errorf("FromInt64ToInt32(%d) = %d, want %d", tt.in, got, tt.want)
			}
		})
	}
}