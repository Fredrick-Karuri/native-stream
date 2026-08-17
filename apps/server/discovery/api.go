package discovery

import (
	"context"
	"net/http"
	"strconv"
	"time"

	"github.com/fredrick-karuri/nativestream/packages/discovery"
	streamv1 "github.com/fredrick-karuri/nativestream/sdk-gen/go/stream/v1"
	"github.com/fredrick-karuri/nativestream/server/httpx"
	"github.com/fredrick-karuri/nativestream/server/intsafe"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type Handler struct {
	engine *discovery.Engine
}

func NewHandler(e *discovery.Engine) *Handler {
	return &Handler{engine: e}
}

func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/discovery/status", h.handleStatus)
	mux.HandleFunc("POST /api/discovery/run", h.handleTriggerRun)
	mux.HandleFunc("GET /api/discovery/unmatched", h.handleUnmatched)
}

func (h *Handler) handleStatus(w http.ResponseWriter, r *http.Request) {
	httpx.WriteProtoJSON(w, http.StatusOK, h.toProtoStatus())
}

func (h *Handler) toProtoStatus() *streamv1.DiscoveryStatusResponse {
	status := h.engine.Status()

	resp := &streamv1.DiscoveryStatusResponse{
		FoundToday:     intsafe.ToInt32(status["found_today"].(int)),
		PromotedToday:  intsafe.ToInt32(status["promoted_today"].(int)),
		UnmatchedCount: intsafe.ToInt32(status["unmatched_count"].(int)),
	}
	if lastRun, ok := status["last_run"].(time.Time); ok && !lastRun.IsZero() {
		resp.LastRun = timestamppb.New(lastRun)
	}
	return resp
}

func (h *Handler) handleTriggerRun(w http.ResponseWriter, r *http.Request) {
	h.engine.TriggerRun(context.Background())
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.StatusResponse{Status: "triggered"})
}

func (h *Handler) handleUnmatched(w http.ResponseWriter, r *http.Request) {
	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if n, err := strconv.Atoi(l); err == nil && n > 0 {
			limit = n
		}
	}
	links := h.engine.Unmatched(limit)
	rows := make([]*streamv1.UnmatchedLink, len(links))
	for i, l := range links {
		rows[i] = &streamv1.UnmatchedLink{Url: l.URL, SourceUrl: l.SourceURL, Context: l.ContextText}
	}
	httpx.WriteProtoJSON(w, http.StatusOK, &streamv1.UnmatchedResponse{
		Unmatched: rows,
		Total:     intsafe.ToInt32(len(rows)),
	})
}
