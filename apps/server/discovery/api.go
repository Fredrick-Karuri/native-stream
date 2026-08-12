// discovery/api.go
package discovery

import (
	"context"
	"net/http"
	"strconv"

	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/httpx"
	"github.com/fredrick-karuri/nativestream/server/intsafe"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func (e *Engine) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/discovery/status", e.handleStatus)
	mux.HandleFunc("POST /api/discovery/run", e.handleTriggerRun)
	mux.HandleFunc("GET /api/discovery/unmatched", e.handleUnmatched)
}

func (e *Engine) handleStatus(w http.ResponseWriter, r *http.Request) {
	httpx.WriteProtoJSON(w, http.StatusOK, e.toProtoStatus())
}

func (e *Engine) toProtoStatus() *streamv1.DiscoveryStatusResponse {
	e.mu.Lock()
	defer e.mu.Unlock()

	resp := &streamv1.DiscoveryStatusResponse{
		FoundToday:     intsafe.ToInt32(e.foundToday),
		PromotedToday:  intsafe.ToInt32(e.promotedToday),
		UnmatchedCount: intsafe.ToInt32(len(e.unmatched)),
	}
	if !e.lastRun.IsZero() {
		resp.LastRun = timestamppb.New(e.lastRun)
	}
	return resp
}

func (e *Engine) handleTriggerRun(w http.ResponseWriter, r *http.Request) {
	e.TriggerRun(context.Background())
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "triggered"})
}

func (e *Engine) handleUnmatched(w http.ResponseWriter, r *http.Request) {
	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil && n > 0 {
			limit = n
		}
	}
	links := e.Unmatched(limit)
	rows := make([]*streamv1.UnmatchedLink, len(links))
	for i, l := range links {
		rows[i] = &streamv1.UnmatchedLink{Url: l.URL, SourceUrl: l.SourceURL, Context: l.ContextText}
	}
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.UnmatchedResponse{
		Unmatched: rows,
		Total:     intsafe.ToInt32(len(rows)),
	})
}
