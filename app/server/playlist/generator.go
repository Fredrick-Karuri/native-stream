// playlist/generator.go
// Generates M3U playlist output from healthy store channels.

package playlist

import (
	"fmt"
	"strings"

	"github.com/fredrick-karuri/nativestream/server/store"
)

type Config struct {
	ProxyEnabled bool
	ServerAddr   string // e.g. "http://127.0.0.1:8888"
}

// Generate returns a complete M3U playlist as a string.
func Generate(channels []*store.Channel, cfg Config) string {
	var sb strings.Builder
	sb.WriteString("#EXTM3U\n")

	for _, ch := range channels {
		if ch.ActiveLink == nil {
			continue
		}
		// Local-script channels always proxy — real URL must not appear in output
		localScript := ch.ActiveLink.SourceURL != "" &&
			!strings.HasPrefix(ch.ActiveLink.SourceURL, "http")


		streamURL := ch.ActiveLink.URL

		if cfg.ProxyEnabled || localScript {
			streamURL = fmt.Sprintf("%s/stream/%s/proxy", cfg.ServerAddr, ch.ID)
		}

		logoAttr := ""
		if ch.LogoURL != "" {
			logoAttr = fmt.Sprintf(` tvg-logo="%s"`, ch.LogoURL)
		}

		sb.WriteString(fmt.Sprintf(
			"#EXTINF:-1 tvg-id=%q%s group-title=%q,%s\n%s\n",
			ch.TvgID,
			logoAttr,
			ch.GroupTitle,
			ch.Name,
			streamURL,
		))
	}

	return sb.String()
}