/*
intsafe/intsafe.go

Overflow-safe integer conversions.
*/
package intsafe

import "math"

// ToInt32 saturates n to the int32 range instead of silently
// wrapping/truncating on overflow (gosec G115).
func ToInt32(n int) int32 {
	if n > math.MaxInt32 {
		return math.MaxInt32
	}
	if n < math.MinInt32 {
		return math.MinInt32
	}
	return int32(n)
}

// FromInt64ToInt32 saturates a 64-bit value to the int32 range instead of
// silently wrapping/truncating on overflow (gosec G115).
func FromInt64ToInt32(n int64) int32 {
	if n > math.MaxInt32 {
		return math.MaxInt32
	}
	if n < math.MinInt32 {
		return math.MinInt32
	}
	return int32(n)
}
