// discovery/circuitbreaker.go — NS-300
// Shared circuit breaker used by all crawlers.
// After maxFailures consecutive failures, the source is suspended for suspendDuration.

package discovery

import (
	"fmt"
	"sync"
	"time"
)

type CircuitBreaker struct {
	mu              sync.Mutex
	failures        map[string]int
	suspendedUntil  map[string]time.Time
	maxFailures     int
	suspendDuration time.Duration
}

func NewCircuitBreaker(maxFailures int, suspendDuration time.Duration) *CircuitBreaker {
	return &CircuitBreaker{
		failures:        make(map[string]int),
		suspendedUntil:  make(map[string]time.Time),
		maxFailures:     maxFailures,
		suspendDuration: suspendDuration,
	}
}

// Allow returns true if the key is allowed to proceed (not suspended).
func (cb *CircuitBreaker) Allow(key string) bool {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	if until, ok := cb.suspendedUntil[key]; ok {
		if time.Now().Before(until) {
			return false
		}
		// Suspension expired — reset
		delete(cb.suspendedUntil, key)
		cb.failures[key] = 0
	}
	return true
}

// RecordSuccess resets the failure count for a key.
func (cb *CircuitBreaker) RecordSuccess(key string) {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.failures[key] = 0
}

// RecordFailure increments failure count and suspends if threshold is reached.
func (cb *CircuitBreaker) RecordFailure(key string) {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.failures[key]++
	if cb.failures[key] >= cb.maxFailures {
		cb.suspendedUntil[key] = time.Now().Add(cb.suspendDuration)
		fmt.Printf("[circuit-breaker] %q suspended for %s after %d failures\n",
			key, cb.suspendDuration, cb.maxFailures)
	}
}

// Status returns a map of key → suspended-until for the health API.
func (cb *CircuitBreaker) Status() map[string]interface{} {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	out := make(map[string]interface{})
	for k, until := range cb.suspendedUntil {
		if time.Now().Before(until) {
			out[k] = map[string]interface{}{
				"suspended":    true,
				"resumes_at":   until,
				"fail_count":   cb.failures[k],
			}
		}
	}
	return out
}