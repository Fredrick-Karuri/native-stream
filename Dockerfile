# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM golang:1.26-alpine AS builder

WORKDIR /build

# Copy VERSION for use in ldflags
COPY VERSION ./VERSION

# Copy the local module referenced by the go.mod replace directive
COPY packages/sdk-gen/go ./packages/sdk-gen/go

# Dependencies first (layer cache) — preserve repo-relative path so the
# "../../packages/sdk-gen/go" replace directive still resolves
COPY apps/server/go.mod apps/server/go.sum ./apps/server/
WORKDIR /build/apps/server
RUN go mod download

# Source
COPY apps/server/ ./

# Build static binary
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-s -w -X main.version=$(cat /build/VERSION 2>/dev/null || echo dev)" \
    -o nativestream-server ./cmd/

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM alpine:3.19

# ca-certificates for HTTPS to GitHub/Reddit/ESPN/etc
RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

COPY --from=builder /build/apps/server/nativestream-server .

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