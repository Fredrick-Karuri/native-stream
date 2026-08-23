// api/pairing_admin_handlers.go
//
// Admin-facing pairing endpoints. All three
// sit behind the standard AuthMiddleware, registered on the same
// authenticated mux as /api/channels — no separate admin-auth system, per
// the design doc's explicit non-goal. Approval is the ticket that ties
// pairing into the existing CredentialStore mechanism: it is the only
// place a pairing session produces a real, revocable credential.

package api

import (
	"errors"
	"net/http"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/httpx"
	"github.com/fredrick-karuri/nativestream/server/store"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// handlePairPending lists all sessions currently pending approval, for an
// operator to match against the code shown on a device's screen.
func (h *Handler) handlePairPending(w http.ResponseWriter, r *http.Request) {
	sessions := h.pairing.Pending()

	resp := &streamv1.PairPendingResponse{
		Sessions: make([]*streamv1.PairingSessionSummary, len(sessions)),
	}
	for i, session := range sessions {
		summary := &streamv1.PairingSessionSummary{
			SessionId:   session.ID,
			Code:        session.Code,
			RequestedAt: timestamppb.New(session.CreatedAt),
		}
		if session.DeviceLabel != "" {
			summary.Platform = proto.String(session.DeviceLabel)
		}
		resp.Sessions[i] = summary
	}
	httpx.WriteProtoJSON(w, http.StatusOK, resp)
}

// handlePairApprove approves a pending session, minting a real credential
// through the existing store.CredentialStore.Create — the single path by
// which a credential comes into existence in the store, matching the
// design doc's core claim.
func (h *Handler) handlePairApprove(w http.ResponseWriter, r *http.Request) {
	sessionID := r.PathValue("session_id")

	var body streamv1.PairApproveRequest
	if err := httpx.ReadProtoJSON(r, &body); err != nil {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "invalid JSON"})
		return
	}
	if body.Label == "" {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "label required"})
		return
	}

	if _, ok := h.pairing.Get(sessionID); !ok {
		httpx.WriteProtoJSON(w, http.StatusNotFound, &streamv1.ErrorResponse{Error: "pairing session not found"})
		return
	}

	cred, err := h.credentials.Create(body.Label)
	if err != nil {
		// Duplicate-label rejection from CredentialStore.Create surfaces
		// here unchanged — approving with a label already in active use
		// fails the same way --create-token does.
		httpx.WriteProtoJSON(w, http.StatusConflict, &streamv1.ErrorResponse{Error: err.Error()})
		return
	}

	session, err := h.pairing.Approve(sessionID, cred.Token)
	if err != nil {
		// The session was pending a moment ago (checked above) but is no
		// longer approvable — e.g. it expired or was denied concurrently.
		// The credential we just created is orphaned but harmless: it's a
		// normal, independently revocable row, same as any --create-token
		// credential nobody happens to be using yet.
		httpx.WriteProtoJSON(w, http.StatusConflict, &streamv1.ErrorResponse{Error: err.Error()})
		return
	}

	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.PairApproveResponse{
		SessionId: session.ID,
		Status:    string(session.Status),
	})
}

// handlePairDeny explicitly rejects a pending pairing session. No
// credential is created.
func (h *Handler) handlePairDeny(w http.ResponseWriter, r *http.Request) {
	sessionID := r.PathValue("session_id")

	if err := h.pairing.Deny(sessionID); err != nil {
		status := http.StatusConflict
		if errors.Is(err, store.ErrPairingSessionNotFound) {
			status = http.StatusNotFound
		}
		httpx.WriteProtoJSON(w, status, &streamv1.ErrorResponse{Error: err.Error()})
		return
	}

	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.PairDenyResponse{
		SessionId: sessionID,
		Status:    string(store.PairingStatusDenied),
	})
}
