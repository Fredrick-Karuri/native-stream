/*
httpx/json.go

Shared helpers for reading and writing protobuf messages as JSON over HTTP.
Used by api and discovery packages to avoid duplicating protojson marshal
configuration (proto field names, zero-value omission) at every call site.
*/
package httpx

import (
	"io"
	"net/http"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

var protoMarshaler = protojson.MarshalOptions{
	UseProtoNames:   true,
	EmitUnpopulated: false,
}

// WriteProtoJSON marshals a proto message to JSON, preserving proto field
// names (snake_case) so existing Swift/Kotlin clients decode it unchanged,
// and writes it as the HTTP response body.
func WriteProtoJSON(w http.ResponseWriter, status int, msg proto.Message) {
	data, err := protoMarshaler.Marshal(msg)
	if err != nil {
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	w.Write(data)
}

// ReadProtoJSON decodes a JSON request body into a proto message.
func ReadProtoJSON(r *http.Request, msg proto.Message) error {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return err
	}
	return protojson.Unmarshal(body, msg)
}