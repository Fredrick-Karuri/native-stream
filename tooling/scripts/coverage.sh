#!/bin/bash
# tooling/scripts/coverage.sh

set -e

TYPE=$1 # server, android, macos, or all
COLOR_BLUE='\033[0;34m'
COLOR_GREEN='\033[0;32m'
COLOR_RED='\033[0;31m'
COLOR_NONE='\033[0m'

ROOT_DIR=$(pwd)
COVERAGE_DIR="$ROOT_DIR/coverage"
mkdir -p "$COVERAGE_DIR"

report_go_coverage() {
    echo -e "${COLOR_BLUE}→ Generating Go Coverage...${COLOR_NONE}"
    GO_COVER_DIR="$COVERAGE_DIR/go"
    mkdir -p "$GO_COVER_DIR"
    
    # Find all directories with a go.mod
    GO_MODS=$(find . -name "go.mod" -not -path "*/node_modules/*")
    
    for mod in $GO_MODS; do
        dir=$(dirname "$mod")
        echo "  Testing $dir..."
        # Create a safe filename for the coverage profile
        safe_name=$(echo "${dir#./}" | tr '/' '_')
        abs_out="$GO_COVER_DIR/${safe_name}.out"
        
        # Run tests and generate profile
        (cd "$dir" && go test -coverprofile="$abs_out" ./... > /dev/null 2>&1) || echo -e "${COLOR_RED}  ! Tests failed in $dir${COLOR_NONE}"
        
        # Calculate total for this directory immediately while in the directory context
        if [ -f "$abs_out" ]; then
            total=$(cd "$dir" && go tool cover -func="$abs_out" | grep total | awk '{print $3}')
            echo "  ${dir#./}: $total"
        fi
    done
}

report_android_coverage() {
    echo -e "${COLOR_BLUE}→ Generating Android Coverage...${COLOR_NONE}"
    cd apps/android
    # Use the specific module task
    if ./gradlew :app:testDebugUnitTestCoverage; then
        echo -e "${COLOR_GREEN}✓ Android coverage generated.${COLOR_NONE}"
        echo "  Report: apps/android/app/build/reports/jacoco/testDebugUnitTestCoverage/html/index.html"
    else
        echo -e "${COLOR_RED}! Android coverage failed. Check if tests passed.${COLOR_NONE}"
    fi
    cd "$ROOT_DIR"
}

report_macos_coverage() {
    echo -e "${COLOR_BLUE}→ Generating macOS Coverage...${COLOR_NONE}"
    APP_DIR="apps/macos/NativeStream"
    DERIVED="$ROOT_DIR/apps/macos/NativeStream/DerivedData"
    
    # Check for xcbeautify
    if command -v xcbeautify >/dev/null 2>&1; then
        FORMATTER="xcbeautify"
    else
        FORMATTER="cat"
    fi

    # Run tests with code coverage enabled
    set -o pipefail && xcodebuild -project "$APP_DIR/NativeStream.xcodeproj" \
               -scheme NativeStream \
               -derivedDataPath "$DERIVED" \
               -enableCodeCoverage YES \
               test | $FORMATTER || echo -e "${COLOR_RED}! macOS Tests failed${COLOR_NONE}"
    
    echo -e "${COLOR_GREEN}✓ macOS coverage data generated in $DERIVED${COLOR_NONE}"
    echo "  To view coverage, open the .xcresult in Xcode or use 'xcrun llvm-cov report'"
}

case $TYPE in
    "server")  report_go_coverage ;;
    "android") report_android_coverage ;;
    "macos")   report_macos_coverage ;;
    "all")
        report_go_coverage
        report_android_coverage
        report_macos_coverage
        ;;
    *)
        echo "Usage: $0 {server|android|macos|all}"
        exit 1
        ;;
esac