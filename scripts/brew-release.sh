#!/bin/bash
# scripts/brew-release.sh — NS-320
# Builds universal macOS binaries and packages them for a Homebrew release.
# Usage: VERSION=4.0.0 ./scripts/brew-release.sh

set -e

VERSION="${VERSION:-4.0.0}"
SERVER_DIR="app/server"
DIST_DIR="dist"

echo "→ Building NativeStream Server v$VERSION"

mkdir -p "$DIST_DIR"

# Apple Silicon
echo "  Building arm64…"
GOOS=darwin GOARCH=arm64 go build \
  -o "$DIST_DIR/nativestream-server" \
  -ldflags "-X main.version=$VERSION" \
  "./$SERVER_DIR/cmd/"

tar -czf "$DIST_DIR/nativestream-server-darwin-arm64.tar.gz" \
  -C "$DIST_DIR" nativestream-server
SHA_ARM64=$(shasum -a 256 "$DIST_DIR/nativestream-server-darwin-arm64.tar.gz" | awk '{print $1}')

# Intel
echo "  Building amd64…"
GOOS=darwin GOARCH=amd64 go build \
  -o "$DIST_DIR/nativestream-server" \
  -ldflags "-X main.version=$VERSION" \
  "./$SERVER_DIR/cmd/"

tar -czf "$DIST_DIR/nativestream-server-darwin-amd64.tar.gz" \
  -C "$DIST_DIR" nativestream-server
SHA_AMD64=$(shasum -a 256 "$DIST_DIR/nativestream-server-darwin-amd64.tar.gz" | awk '{print $1}')

rm "$DIST_DIR/nativestream-server"

echo ""
echo "✓ Release assets in $DIST_DIR/"
echo ""
echo "Update homebrew/nativestream-server.rb with:"
echo "  arm64 sha256: $SHA_ARM64"
echo "  amd64 sha256: $SHA_AMD64"
echo ""
echo "Then push to your tap and run:"
echo "  brew install fredrick-karuri/nativestream/nativestream-server"