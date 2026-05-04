// discovery/api.go — NS-230, NS-231
package discovery

import (
	"encoding/json"
	"net/http"
	"strconv"
)

func (e *Engine) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/discovery/status", e.handleStatus)
	mux.HandleFunc("POST /api/discovery/run", e.handleTriggerRun)
	mux.HandleFunc("GET /api/discovery/unmatched", e.handleUnmatched)
}

func (e *Engine) handleStatus(w http.ResponseWriter, r *http.Request) {
	discoveryWriteJSON(w, http.StatusOK, e.Status())
}

func (e *Engine) handleTriggerRun(w http.ResponseWriter, r *http.Request) {
	e.TriggerRun(r.Context())
	discoveryWriteJSON(w, http.StatusOK, map[string]string{"status": "triggered"})
}

func (e *Engine) handleUnmatched(w http.ResponseWriter, r *http.Request) {
	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil && n > 0 {
			limit = n
		}
	}
	links := e.Unmatched(limit)
	type row struct {
		URL       string `json:"url"`
		SourceURL string `json:"source_url"`
		Context   string `json:"context"`
	}
	rows := make([]row, len(links))
	for i, l := range links {
		rows[i] = row{URL: l.URL, SourceURL: l.SourceURL, Context: l.ContextText}
	}
	discoveryWriteJSON(w, http.StatusOK, map[string]interface{}{"unmatched": rows, "total": len(rows)})
}

func discoveryWriteJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}