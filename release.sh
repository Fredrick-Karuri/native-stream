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

# Checks whether $TAG already exists locally or on origin. If so, prompts
# the user to delete and retag rather than failing with a bare git error.
handle_existing_tag() {
    local tag=$1
    local tag_exists_locally=false
    local tag_exists_remotely=false

    if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
        tag_exists_locally=true
    fi
    if git ls-remote --exit-code --tags origin "$tag" >/dev/null 2>&1; then
        tag_exists_remotely=true
    fi

    if [ "$tag_exists_locally" = false ] && [ "$tag_exists_remotely" = false ]; then
        return 0
    fi

    echo "⚠️  Tag '$tag' already exists (local: $tag_exists_locally, remote: $tag_exists_remotely)."
    read -r -p "Delete and retag? This will re-trigger the release workflow. [y/N] " confirm
    case "$confirm" in
        y|Y|yes|YES)
            if [ "$tag_exists_locally" = true ]; then
                git tag -d "$tag"
            fi
            if [ "$tag_exists_remotely" = true ]; then
                git push origin ":refs/tags/$tag"
            fi
            ;;
        *)
            echo "Aborted. No tag was created."
            exit 1
            ;;
    esac
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

echo "Targeting Tag Creation: $TAG"
handle_existing_tag "$TAG"
git tag "$TAG"
git push origin main --tags
echo "🚀 Release process initiated on GitHub for $TAG!"