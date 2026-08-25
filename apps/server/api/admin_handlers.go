// api/admin_handlers.go
//
// Serves the minimal web admin page. One
// server-rendered HTML document with embedded JS calling the existing
// pairing/credential JSON APIs via fetch — no build step, no SPA
// framework, consistent with the "keep the server dependency-free"
// decision. The page itself sits behind the same AuthMiddleware as
// /api/*; there is no separate admin-auth system.

package api

import (
	_ "embed"
	"net/http"
)

//go:embed admin.html
var adminHTML []byte

// handleAdminPage serves the single-page admin UI. All data on the page is
// fetched client-side from the existing authenticated JSON endpoints — the
// HTML itself contains no server state, so this handler is a static byte
// serve, not a template render.
func (h *Handler) handleAdminPage(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	if _, err := w.Write(adminHTML); err != nil {
		return
	}
}
