#!/bin/bash
# NativeStream — Build & notarise release DMG
# Usage: ./scripts/release.sh
# Prerequisites: Xcode 15+, Apple Developer ID cert, notarytool configured

set -e

SCHEME="NativeStream"
APP_DIR="app/macos"
DERIVED="$APP_DIR/DerivedData"
EXPORT_DIR="$APP_DIR/Export"
ARCHIVE="$APP_DIR/NativeStream.xcarchive"
DMG_NAME="NativeStream.dmg"
APPLE_ID="${APPLE_ID:-}"       # set in env or export APPLE_ID=you@email.com
TEAM_ID="${TEAM_ID:-}"         # set in env or export TEAM_ID=XXXXXXXXXX

echo ""
echo "→ Archiving..."
xcodebuild archive \
  -project "$APP_DIR/NativeStream.xcodeproj" \
  -scheme "$SCHEME" \
  -configuration Release \
  -archivePath "$ARCHIVE" \
  -derivedDataPath "$DERIVED"

echo "→ Exporting..."
xcodebuild -exportArchive \
  -archivePath "$ARCHIVE" \
  -exportPath "$EXPORT_DIR" \
  -exportOptionsPlist scripts/ExportOptions.plist

echo "→ Creating DMG..."
hdiutil create -volname "NativeStream" \
  -srcfolder "$EXPORT_DIR/NativeStream.app" \
  -ov -format UDZO "$DMG_NAME"

if [ -n "$APPLE_ID" ] && [ -n "$TEAM_ID" ]; then
  echo "→ Notarising..."
  xcrun notarytool submit "$DMG_NAME" \
    --apple-id "$APPLE_ID" \
    --team-id "$TEAM_ID" \
    --wait

  echo "→ Stapling..."
  xcrun stapler staple "$DMG_NAME"
  echo "✓ Done: $DMG_NAME (notarised)"
else
  echo "⚠ Skipping notarisation — set APPLE_ID and TEAM_ID env vars to notarise."
  echo "✓ Done: $DMG_NAME (unsigned)"
fi