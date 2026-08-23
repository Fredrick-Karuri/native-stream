// api/pairing_handlers.go
//
// Device-facing pairing endpoints. Both are mounted
// unauthenticated — a device with no credential yet must be able to start
// and poll a pairing handshake — so both are also rate-limited per source
// IP and scoped to reveal the minimum possible on failure. Admin-facing
// pairing endpoints (pending/approve/deny) live in
// pairing_admin_handlers.go and sit behind the normal AuthMiddleware.

package api

import (
	"net/http"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/httpx"
	"github.com/fredrick-karuri/nativestream/server/store"
	"google.golang.org/protobuf/proto"
)

// pairingSessionTTLSeconds mirrors the TTL enforced by
// store.PairingSessionStore; surfaced to the device so it knows how long
// its code is valid for.
const pairingSessionTTLSeconds = 60

// handlePairStart begins a new pairing session for a device with no
// credential yet. Unauthenticated by construction — see PAIR-002.
func (h *Handler) handlePairStart(w http.ResponseWriter, r *http.Request) {
	if !h.pairStartLimiter.Allow(clientIP(r)) {
		httpx.WriteProtoJSON(w, http.StatusTooManyRequests, &streamv1.ErrorResponse{Error: "too many pairing attempts, try again shortly"})
		return
	}

	var body streamv1.PairStartRequest
	// A malformed or empty body is fine — platform is optional context,
	// not a required field, so we don't fail the request on decode error.
	_ = httpx.ReadProtoJSON(r, &body)

	session, err := h.pairing.Start(body.Platform)
	if err != nil {
		httpx.WriteProtoJSON(w, http.StatusInternalServerError, &streamv1.ErrorResponse{Error: "could not start pairing session"})
		return
	}

	httpx.WriteProtoJSON(w, http.StatusCreated, &streamv1.PairStartResponse{
		SessionId:        session.ID,
		Code:             session.Code,
		ExpiresInSeconds: pairingSessionTTLSeconds,
	})
}

// handlePairStatus reports the status of one pairing session by ID. Scoped
// strictly to the session ID in the path — never accepts or reveals the
// human-facing code, never lists other sessions. An unknown session ID
// reports the same shape as an expired one, so probing session IDs learns
// nothing (see PAIR-003 done-when).
func (h *Handler) handlePairStatus(w http.ResponseWriter, r *http.Request) {
	if !h.pairStatusLimiter.Allow(clientIP(r)) {
		httpx.WriteProtoJSON(w, http.StatusTooManyRequests, &streamv1.ErrorResponse{Error: "too many status checks, try again shortly"})
		return
	}

	sessionID := r.PathValue("session_id")

	session, ok := h.pairing.Get(sessionID)
	if !ok {
		httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.PairStatusResponse{Status: string(store.PairingStatusExpired)})
		return
	}

	resp := &streamv1.PairStatusResponse{Status: string(session.Status)}
	if session.Status == store.PairingStatusApproved {
		resp.Token = proto.String(session.ResultToken)
		resp.Label = proto.String(session.DeviceLabel)
	}
	httpx.WriteProtoJSON(w, http.StatusOK, resp)
}
