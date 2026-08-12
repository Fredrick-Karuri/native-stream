// proxy/client_test.go
//
// newClient() returns an *http.Client whose only interesting logic is the
// CheckRedirect closure — a redirect-count limiter. Testing "the real
// client actually stops redirecting after 5 hops" would need a live HTTP
// server issuing real 3xx responses; instead we call the closure directly
// with synthetic request slices, which is the actual decision logic and
// needs no network.

package proxy

import (
	"net/http"
	"testing"
)

func TestNewClient_CheckRedirect_AllowsUpToFiveRedirects(t *testing.T) {
	client := newClient()
	if client.CheckRedirect == nil {
		t.Fatal("newClient().CheckRedirect is nil, want a redirect-limiting func")
	}

	req := &http.Request{}
	for n := 0; n < 5; n++ {
		via := make([]*http.Request, n)
		if err := client.CheckRedirect(req, via); err != nil {
			t.Errorf("CheckRedirect with %d prior redirects = %v, want nil (should still be allowed)", n, err)
		}
	}
}

func TestNewClient_CheckRedirect_RejectsSixthRedirect(t *testing.T) {
	client := newClient()

	req := &http.Request{}
	via := make([]*http.Request, 5) // 5 redirects already made — the 6th should be rejected

	if err := client.CheckRedirect(req, via); err == nil {
		t.Error("CheckRedirect with 5 prior redirects = nil, want error rejecting the 6th hop")
	}
}

func TestNewClient_CheckRedirect_RejectsWellBeyondLimit(t *testing.T) {
	client := newClient()

	req := &http.Request{}
	via := make([]*http.Request, 20)

	if err := client.CheckRedirect(req, via); err == nil {
		t.Error("CheckRedirect with 20 prior redirects = nil, want error")
	}
}

func TestNewClient_CheckRedirect_AllowsFirstRedirectWithEmptyVia(t *testing.T) {
	client := newClient()

	req := &http.Request{}
	if err := client.CheckRedirect(req, nil); err != nil {
		t.Errorf("CheckRedirect with nil via (first redirect) = %v, want nil", err)
	}
}
