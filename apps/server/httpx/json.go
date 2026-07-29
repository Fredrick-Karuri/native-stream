// httpx/json.go
package httpx

import (
	"net/http"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

var protoMarshaler = protojson.MarshalOptions{
	UseProtoNames:   true,
	EmitUnpopulated: false,
}

// WriteProtoJSON marshals a proto message to JSON (using proto field names,
// e.g. snake_case) and writes it as the HTTP response body.
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