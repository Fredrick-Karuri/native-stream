// api/credential_handlers.go
//
// Admin-facing credential management. Both endpoints sit behind the standard
// AuthMiddleware, same mux as everything else. Neither ever returns a raw
// token — CredentialSummary deliberately has no token field, matching the
// existing --list-tokens CLI behavior (tokens are shown once, at
// creation, never again).

package api

import (
	"net/http"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/httpx"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// handleListCredentials lists every credential via the existing
// CredentialStore.All() — no new listing logic, per PAIR-010's done-when.
func (h *Handler) handleListCredentials(w http.ResponseWriter, r *http.Request) {
	all := h.credentials.All()

	resp := &streamv1.CredentialListResponse{
		Credentials: make([]*streamv1.CredentialSummary, len(all)),
	}
	for i, cred := range all {
		summary := &streamv1.CredentialSummary{
			Label:     cred.Label,
			CreatedAt: timestamppb.New(cred.CreatedAt),
		}
		if cred.RevokedAt != nil {
			summary.RevokedAt = timestamppb.New(*cred.RevokedAt)
		}
		resp.Credentials[i] = summary
	}
	httpx.WriteProtoJSON(w, http.StatusOK, resp)
}

// handleRevokeCredential revokes a credential by label, calling the
// existing CredentialStore.Revoke — no new server-side revocation code,
// per PAIR-010's done-when.
func (h *Handler) handleRevokeCredential(w http.ResponseWriter, r *http.Request) {
	var body streamv1.RevokeCredentialRequest
	if err := httpx.ReadProtoJSON(r, &body); err != nil {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "invalid JSON"})
		return
	}
	if body.Label == "" {
		httpx.WriteProtoJSON(w, http.StatusBadRequest, &streamv1.ErrorResponse{Error: "label required"})
		return
	}

	if err := h.credentials.Revoke(body.Label); err != nil {
		httpx.WriteProtoJSON(w, http.StatusNotFound, &streamv1.ErrorResponse{Error: err.Error()})
		return
	}

	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "revoked"})
}
