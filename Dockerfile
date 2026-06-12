# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM golang:1.25-alpine AS builder

WORKDIR /build

# Dependencies first (layer cache)
COPY app/server/go.mod ./
RUN go mod download

# Source
COPY app/server/ ./

# Build static binary
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-s -w -X main.version=$(cat /build/../../VERSION 2>/dev/null || echo dev)" \
    -o nativestream-server ./cmd/

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM alpine:3.19

# ca-certificates for HTTPS to GitHub/Reddit/ESPN/etc
RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

COPY --from=builder /build/nativestream-server .

# Config and data directories
RUN mkdir -p /config /data

# Default config will be mounted at /config/config.yaml
# Data (channels.json, epg_cache.xml) written to /data

EXPOSE 8888

# Run as non-root
RUN addgroup -S nativestream && adduser -S nativestream -G nativestream
RUN chown -R nativestream:nativestream /app /config /data
USER nativestream

ENTRYPOINT ["./nativestream-server"]