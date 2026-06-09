#!/bin/bash
set -e

if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: ./release.sh [server|macos|android] [patch|minor|major|current]"
    echo "Example: ./release.sh android patch"
    exit 1
fi

COMPONENT=$1
BUMP_TYPE=$2

# SemVer bumper math engine
bump_version() {
    local current=$1
    local type=$2
    # Ensure there are 3 segments (X.Y.Z) even if Xcode outputs a 2-segment string like "1.0"
    if [[ ! $current =~ \.[0-9]+\.[0-9]+ ]]; then current="${current}.0"; fi
    IFS='.' read -r major minor patch <<< "$current"
    case "$type" in
        patch) patch=$((patch+1)) ;;
        minor) minor=$((minor+1)); patch=0 ;;
        major) major=$((major+1)); minor=0; patch=0 ;;
    esac
    echo "$major.$minor.$patch"
}

case $COMPONENT in
    server)
        VERSION_FILE="app/server/VERSION"
        CURRENT_VERSION=$(cat "$VERSION_FILE" | tr -d '[:space:]')
        NEW_VERSION=$CURRENT_VERSION
        if [ "$BUMP_TYPE" != "current" ]; then
            NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP_TYPE")
            echo "$NEW_VERSION" > "$VERSION_FILE"
            git add "$VERSION_FILE"
            git commit -m "chore(server): bump version to $NEW_VERSION" || true
        fi
        TAG="server/v$NEW_VERSION"
        ;;

    android)
        GRADLE_FILE="app/android/app/build.gradle.kts"
        CURRENT_VERSION=$(grep "versionName =" "$GRADLE_FILE" | head -n 1 | awk -F '"' '{print $2}')
        CURRENT_CODE=$(grep "versionCode =" "$GRADLE_FILE" | head -n 1 | tr -dc '0-9')
        NEW_VERSION=$CURRENT_VERSION
        if [ "$BUMP_TYPE" != "current" ]; then
            NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP_TYPE")
            NEW_CODE=$((CURRENT_CODE+1))
            
            # Cross-platform safe file editing (macOS/Linux compatibility)
            sed -i.bak "s/versionCode = .*/versionCode = $NEW_CODE/" "$GRADLE_FILE"
            sed -i.bak "s/versionName = .*/versionName = \"$NEW_VERSION\"/" "$GRADLE_FILE"
            rm -f "${GRADLE_FILE}.bak"
            
            git add "$GRADLE_FILE"
            git commit -m "chore(android): bump version to $NEW_VERSION" || true
        fi
        TAG="android/v$NEW_VERSION"
        ;;

    macos)
        PBXPROJ="app/macos/NativeStream/NativeStream.xcodeproj/project.pbxproj"
        CURRENT_VERSION=$(grep "MARKETING_VERSION =" "$PBXPROJ" | head -n 1 | awk -F '= ' '{print $2}' | tr -d ';[:space:]')
        CURRENT_CODE=$(grep "CURRENT_PROJECT_VERSION =" "$PBXPROJ" | head -n 1 | awk -F '= ' '{print $2}' | tr -d ';[:space:]')
        NEW_VERSION=$CURRENT_VERSION
        if [ "$BUMP_TYPE" != "current" ]; then
            NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP_TYPE")
            NEW_CODE=$((CURRENT_CODE+1))
            
            # Mutates marketing and project build versions safely across all Xcode build targets
            sed -i.bak "s/MARKETING_VERSION = .*/MARKETING_VERSION = $NEW_VERSION;/" "$PBXPROJ"
            sed -i.bak "s/CURRENT_PROJECT_VERSION = .*/CURRENT_PROJECT_VERSION = $NEW_CODE;/" "$PBXPROJ"
            rm -f "${PBXPROJ}.bak"
            
            git add "$PBXPROJ"
            git commit -m "chore(macos): bump version to $NEW_VERSION" || true
        fi
        TAG="macos/v$NEW_VERSION"
        ;;
    *)
        echo "Error: Unknown component block '$COMPONENT'"
        exit 1
        ;;
esac

# Extract the active local branch name automatically
CURRENT_BRANCH=$(git branch --show-current)

echo "Targeting Tag Creation: $TAG"
git tag "$TAG" 2>/dev/null || echo "Local tag already exists, attempting push..."

# FIXED: Replaced hardcoded 'main' with the active branch variable
git push origin "$CURRENT_BRANCH" --tags
echo "🚀 Release process initiated on GitHub for $TAG on branch $CURRENT_BRANCH!"

