// store/pairing.go
//
// In-memory pairing session store. Lets a device with no
// credential yet start a short-lived pairing handshake: it gets a
// human-readable code and a session ID, an operator matches the code to
// the device and approves it, and approval mints a real row in
// CredentialStore via the existing Create method. PairingSession is
// deliberately NOT part of CredentialStore — it is transient handshake
// state, not a credential, and never persists to disk. A session lost on
// restart simply means the device retries pair/start.

package store

import (
	"crypto/rand"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
)

const pairingCodeLength = 6

const pairingSessionTTL = 60 * time.Second

// pairingCodeAlphabet excludes visually ambiguous characters (0/O, 1/I) so
// a human reading the code off a screen and typing it elsewhere doesn't
// mistype it.
const pairingCodeAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

// PairingStatus is the lifecycle state of a PairingSession. A session
// transitions pending -> approved, pending -> denied, or pending -> expired
// (the last one implicitly, via lazy check on read) and never anywhere else.
type PairingStatus string

const (
	PairingStatusPending  PairingStatus = "pending"
	PairingStatusApproved PairingStatus = "approved"
	PairingStatusDenied   PairingStatus = "denied"
	PairingStatusExpired  PairingStatus = "expired"
)

// PairingSession is one in-flight (or resolved) pairing handshake.
type PairingSession struct {
	ID          string
	Code        string
	CreatedAt   time.Time
	ExpiresAt   time.Time
	Status      PairingStatus
	DeviceLabel string
	ResultToken string
}

// currentStatus returns Status as it should be reported right now, applying
// lazy expiry: a pending session past ExpiresAt reads as expired without
// requiring a background sweep. Approved/denied sessions are terminal and
// are returned as-is regardless of ExpiresAt.
func (ps *PairingSession) currentStatus(now time.Time) PairingStatus {
	if ps.Status == PairingStatusPending && now.After(ps.ExpiresAt) {
		return PairingStatusExpired
	}
	return ps.Status
}

// PairingSessionStore is an in-memory, non-persisted table of PairingSession
// rows, keyed by session ID. Safe for concurrent use.
type PairingSessionStore struct {
	mu       sync.RWMutex
	sessions map[string]*PairingSession
}

// NewPairingSessionStore creates an empty pairing session store.
func NewPairingSessionStore() *PairingSessionStore {
	return &PairingSessionStore{
		sessions: make(map[string]*PairingSession),
	}
}

// Start creates a new pending PairingSession with a freshly generated,
// collision-checked code and session ID, and returns it.
func (pss *PairingSessionStore) Start(deviceLabel string) (*PairingSession, error) {
	pss.mu.Lock()
	defer pss.mu.Unlock()

	code, err := pss.generateUniqueCodeLocked()
	if err != nil {
		return nil, fmt.Errorf("generate pairing code: %w", err)
	}

	now := time.Now()
	session := &PairingSession{
		ID:          uuid.NewString(),
		Code:        code,
		CreatedAt:   now,
		ExpiresAt:   now.Add(pairingSessionTTL),
		Status:      PairingStatusPending,
		DeviceLabel: deviceLabel,
	}

	pss.sessions[session.ID] = session
	return session, nil
}

// Get returns a snapshot of the session with the given ID, with its Status
// resolved through lazy expiry. Returns nil, false if no session with that
// ID has ever existed (callers should treat this identically to an expired
// session in any response shape, so as not to leak which session IDs are
// real).
func (pss *PairingSessionStore) Get(sessionID string) (*PairingSession, bool) {
	pss.mu.RLock()
	defer pss.mu.RUnlock()

	session, ok := pss.sessions[sessionID]
	if !ok {
		return nil, false
	}

	cp := *session
	cp.Status = session.currentStatus(time.Now())
	return &cp, true
}

// Pending returns a snapshot of every session currently in pending status,
// with expired sessions filtered out rather than left for the caller to
// ignore.
func (pss *PairingSessionStore) Pending() []*PairingSession {
	pss.mu.RLock()
	defer pss.mu.RUnlock()

	now := time.Now()
	out := make([]*PairingSession, 0, len(pss.sessions))
	for _, session := range pss.sessions {
		if session.currentStatus(now) != PairingStatusPending {
			continue
		}
		cp := *session
		out = append(out, &cp)
	}
	return out
}

// Approve transitions the session to approved and attaches resultToken,
// the credential minted for it. Returns an error if the session doesn't
// exist or isn't currently pending (already approved, denied, or expired)
// — approval is never silently reapplied.
func (pss *PairingSessionStore) Approve(sessionID, resultToken string) (*PairingSession, error) {
	pss.mu.Lock()
	defer pss.mu.Unlock()

	session, ok := pss.sessions[sessionID]
	if !ok {
		return nil, fmt.Errorf("pairing session store: no session %q", sessionID)
	}

	status := session.currentStatus(time.Now())
	if status != PairingStatusPending {
		return nil, fmt.Errorf("pairing session store: session %q is %s, cannot approve", sessionID, status)
	}

	session.Status = PairingStatusApproved
	session.ResultToken = resultToken

	cp := *session
	return &cp, nil
}

// Deny transitions the session to denied. Returns an error if the session
// doesn't exist or isn't currently pending.
func (pss *PairingSessionStore) Deny(sessionID string) error {
	pss.mu.Lock()
	defer pss.mu.Unlock()

	session, ok := pss.sessions[sessionID]
	if !ok {
		return fmt.Errorf("pairing session store: no session %q", sessionID)
	}

	status := session.currentStatus(time.Now())
	if status != PairingStatusPending {
		return fmt.Errorf("pairing session store: session %q is %s, cannot deny", sessionID, status)
	}

	session.Status = PairingStatusDenied
	return nil
}

// generateUniqueCodeLocked generates a pairing code not currently held by
// any live (pending, non-expired) session. Caller must hold pss.mu.
func (pss *PairingSessionStore) generateUniqueCodeLocked() (string, error) {
	now := time.Now()
	for attempt := 0; attempt < maxCodeGenerationAttempts; attempt++ {
		code, err := randomPairingCode()
		if err != nil {
			return "", err
		}

		collision := false
		for _, session := range pss.sessions {
			if session.Code == code && session.currentStatus(now) == PairingStatusPending {
				collision = true
				break
			}
		}
		if !collision {
			return code, nil
		}
	}
	return "", fmt.Errorf("could not generate a unique pairing code after %d attempts", maxCodeGenerationAttempts)
}

// maxCodeGenerationAttempts bounds retries when a freshly generated code
// collides with a still-live session. Collisions are expected to be
// vanishingly rare given ~1.5 billion possible codes and a 60s TTL, so a
// small retry budget is purely defensive.
const maxCodeGenerationAttempts = 10

// randomPairingCode returns one random code drawn from pairingCodeAlphabet,
// pairingCodeLength characters long.
func randomPairingCode() (string, error) {
	buf := make([]byte, pairingCodeLength)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}

	code := make([]byte, pairingCodeLength)
	alphabetLen := byte(len(pairingCodeAlphabet))
	for i, b := range buf {
		code[i] = pairingCodeAlphabet[b%alphabetLen]
	}
	return string(code), nil
}
