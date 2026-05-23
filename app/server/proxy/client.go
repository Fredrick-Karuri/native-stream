// proxy/client.go
// Constructs the shared HTTP client used for upstream requests.

package proxy

import (
	"fmt"
	"net/http"
)

func newClient() *http.Client {
	return &http.Client{
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return fmt.Errorf("too many redirects")
			}
			return nil
		},
	}
}