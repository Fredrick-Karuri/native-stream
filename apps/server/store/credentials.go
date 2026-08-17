// store/credentials.go
//
// Per-person credential store (CPMP-001). Replaces the single shared
// Server.APIToken config value with a persistent table of independently
// revocable tokens. Deliberately minimal: no user profile, no email, no
// password — label is a free-text operator note, not an identity system.

package store

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

const credentialTokenBytes = 32

// Credential is one row in the credential store: a token, an operator-facing
// label, and revocation state. Revocation sets RevokedAt rather than deleting
// the row, preserving an audit trail (CPMP-001 done-when).
type Credential struct {
	Token     string     `json:"token"`
	Label     string     `json:"label"`
	CreatedAt time.Time  `json:"created_at"`
	RevokedAt *time.Time `json:"revoked_at,omitempty"`
}

// IsRevoked reports whether this credential has been revoked.
func (c *Credential) IsRevoked() bool {
	return c.RevokedAt != nil
}

// CredentialStore is a persisted, in-memory table of Credential rows, keyed
// by token. Safe for concurrent use.
type CredentialStore struct {
	mu          sync.RWMutex
	credentials map[string]*Credential
	path        string
}

// NewCredentialStore creates a store persisted to path (a JSON file).
func NewCredentialStore(path string) *CredentialStore {
	return &CredentialStore{
		credentials: make(map[string]*Credential),
		path:        path,
	}
}

// Create generates a new unique token, stores a Credential for it under
// label, persists the store, and returns the created row.
func (cs *CredentialStore) Create(label string) (*Credential, error) {
	token, err := generateToken()
	if err != nil {
		return nil, fmt.Errorf("generate token: %w", err)
	}

	cred := &Credential{
		Token:     token,
		Label:     label,
		CreatedAt: time.Now(),
	}

	cs.mu.Lock()
	cs.credentials[cred.Token] = cred
	cs.mu.Unlock()

	if err := cs.save(); err != nil {
		return nil, fmt.Errorf("persist credential: %w", err)
	}
	return cred, nil
}

// IsValid reports whether token matches a non-revoked credential in the
// store. Uses a constant-time comparison per-row so that iterating the set
// doesn't leak timing information about how close a supplied token is to
// any single stored value (CPMP-002 done-when).
func (cs *CredentialStore) IsValid(token string) bool {
	cs.mu.RLock()
	defer cs.mu.RUnlock()
	for _, cred := range cs.credentials {
		if cred.IsRevoked() {
			continue
		}
		if subtle.ConstantTimeCompare([]byte(token), []byte(cred.Token)) == 1 {
			return true
		}
	}
	return false
}

// Revoke marks the credential with the given label as revoked, taking
// effect immediately on the in-memory store (no restart required). Returns
// an error if no non-revoked credential with that label exists.
func (cs *CredentialStore) Revoke(label string) error {
	cs.mu.Lock()
	var target *Credential
	for _, cred := range cs.credentials {
		if cred.Label == label && !cred.IsRevoked() {
			target = cred
			break
		}
	}
	if target == nil {
		cs.mu.Unlock()
		return fmt.Errorf("credential store: no active credential labeled %q", label)
	}
	now := time.Now()
	target.RevokedAt = &now
	cs.mu.Unlock()

	return cs.save()
}

// All returns a snapshot of every credential, revoked or not, for admin/CLI
// listing use.
func (cs *CredentialStore) All() []*Credential {
	cs.mu.RLock()
	defer cs.mu.RUnlock()
	out := make([]*Credential, 0, len(cs.credentials))
	for _, cred := range cs.credentials {
		cp := *cred
		out = append(out, &cp)
	}
	return out
}

// Count returns total and active (non-revoked) credential counts.
func (cs *CredentialStore) Count() (total, active int) {
	cs.mu.RLock()
	defer cs.mu.RUnlock()
	total = len(cs.credentials)
	for _, cred := range cs.credentials {
		if !cred.IsRevoked() {
			active++
		}
	}
	return
}

// ── Persistence ──────────────────────────────────────────────────────────────

type credentialSnapshot struct {
	Version     int           `json:"version"`
	UpdatedAt   time.Time     `json:"updated_at"`
	Credentials []*Credential `json:"credentials"`
}

// Load reads the credential store from disk. If the file does not exist,
// this is a fresh install and Load is a no-op (not an error).
func (cs *CredentialStore) Load() error {
	data, err := os.ReadFile(cs.path)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("read credential store: %w", err)
	}

	var snap credentialSnapshot
	if err := json.Unmarshal(data, &snap); err != nil {
		return fmt.Errorf("parse credential store: %w", err)
	}

	cs.mu.Lock()
	defer cs.mu.Unlock()
	cs.credentials = make(map[string]*Credential, len(snap.Credentials))
	for _, cred := range snap.Credentials {
		cs.credentials[cred.Token] = cred
	}
	return nil
}

// save atomically writes the credential store to disk.
func (cs *CredentialStore) save() error {
	cs.mu.RLock()
	snap := credentialSnapshot{
		Version:     1,
		UpdatedAt:   time.Now(),
		Credentials: make([]*Credential, 0, len(cs.credentials)),
	}
	for _, cred := range cs.credentials {
		cp := *cred
		snap.Credentials = append(snap.Credentials, &cp)
	}
	cs.mu.RUnlock()

	data, err := json.MarshalIndent(snap, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal credential store: %w", err)
	}

	if err := os.MkdirAll(filepath.Dir(cs.path), 0o750); err != nil {
		return fmt.Errorf("create credential store dir: %w", err)
	}

	tmp := cs.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return fmt.Errorf("write credential store tmp: %w", err)
	}
	if err := os.Rename(tmp, cs.path); err != nil {
		return fmt.Errorf("rename credential store: %w", err)
	}
	return nil
}

// MigrateLegacyToken imports a pre-existing single Server.APIToken (HOST-002)
// as one credential, so devices already using it keep working without a
// forced re-auth. No-op if legacyToken is empty or a credential already
// exists (migration only ever runs once, on first boot into the credential
// model).
func (cs *CredentialStore) MigrateLegacyToken(legacyToken string) error {
	if legacyToken == "" {
		return nil
	}

	cs.mu.RLock()
	_, alreadyMigrated := cs.credentials[legacyToken]
	hasAny := len(cs.credentials) > 0
	cs.mu.RUnlock()

	if alreadyMigrated || hasAny {
		return nil
	}

	cred := &Credential{
		Token:     legacyToken,
		Label:     "Migrated from single api_token",
		CreatedAt: time.Now(),
	}

	cs.mu.Lock()
	cs.credentials[cred.Token] = cred
	cs.mu.Unlock()

	return cs.save()
}

// generateToken returns a random 32-byte token, hex-encoded.
func generateToken() (string, error) {
	buf := make([]byte, credentialTokenBytes)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}
