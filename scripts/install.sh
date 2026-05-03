#!/bin/bash
# NativeStream — First-time setup script
# Run: ./scripts/install.sh

set -e

CONFIG_DIR="$HOME/.config/nativestream"
CONFIG_FILE="$CONFIG_DIR/config.yaml"
EXAMPLE_CONFIG="$(dirname "$0")/../config/config.example.yaml"

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   NativeStream — Setup              ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Create config directory
if [ ! -d "$CONFIG_DIR" ]; then
  mkdir -p "$CONFIG_DIR"
  echo "✓ Created $CONFIG_DIR"
else
  echo "✓ Config directory already exists"
fi

# Copy example config if none exists
if [ ! -f "$CONFIG_FILE" ]; then
  cp "$EXAMPLE_CONFIG" "$CONFIG_FILE"
  echo "✓ Created config at $CONFIG_FILE"
  echo ""
  echo "  → Open $CONFIG_FILE and add your playlist sources."
else
  echo "✓ Config already exists at $CONFIG_FILE"
fi

echo ""
echo "Next steps:"
echo "  1. Edit $CONFIG_FILE"
echo "  2. make build-server"
echo "  3. make run-server"
echo "  4. Open NativeStreamMac.app"
echo ""