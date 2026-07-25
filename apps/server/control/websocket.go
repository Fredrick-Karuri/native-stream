// server/control/websocket.go
//
// WebSocket client read loop — reads inbound messages from a connected
// device and dispatches them to the Hub. Runs in its own goroutine per
// client, exits on connection close or context cancellation.

package control

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"
)

const readTimeout = 70 * time.Second // slightly longer than 2 missed pings (60s)

// ReadLoop reads messages from the client connection until it closes.
// All inbound envelopes are dispatched to the hub for routing.
// Registration envelope is handled inline before the loop starts.
func ReadLoop(ctx context.Context, hub *Hub, client *Client) {
	defer hub.Unregister(client)
	defer client.Conn.CloseNow()

	for {
		readCtx, cancel := context.WithTimeout(ctx, readTimeout)
		_, data, err := client.Conn.Read(readCtx)
		cancel()

		if err != nil {
			slog.Debug("lmc: client read closed", "device_id", client.DeviceID, "err", err)
			return
		}

		var env Envelope
		if err := json.Unmarshal(data, &env); err != nil {
			slog.Warn("lmc: bad envelope from client", "device_id", client.DeviceID, "err", err)
			continue
		}

		// Stamp from field with the registered device ID — don't trust client-supplied from
		env.From = client.DeviceID
		hub.Dispatch(env)
	}
}