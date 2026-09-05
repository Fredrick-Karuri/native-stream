.PHONY: build test lint build-server run-server clean build-mac run-mac lint-mac dev  \
		test-server test-android-unit test-android-ui test-android-all \
        release-server-patch release-server-minor release-server-major release-server-current \
        release-android-patch release-android-minor release-android-major release-android-current \
        release-macos-patch release-macos-minor release-macos-major release-macos-current test-mac-unit test-mac-ui \
		coverage-server coverage-android coverage-mac coverage-all

# ── Go Server ─────────────────────────────────────────────────────────────────
SERVER_DIR := apps/server
SERVER_BIN := $(SERVER_DIR)/nativestream-server

build-server:
	@echo "→ Building NativeStream Server..."
	cd $(SERVER_DIR) && go build -o nativestream-server ./cmd/
	@echo "✓ Binary: $(SERVER_BIN)"

run-server: build-server
	@echo "→ Starting server on http://127.0.0.1:8889"
	NATIVESTREAM_CONFIG=~/.config/nativestream/config.dev.yaml $(SERVER_BIN)

test-server:
	@echo "→ Running Go tests..."
	cd $(SERVER_DIR) && go test -race -v ./...

vet-server:
	cd $(SERVER_DIR) && go vet ./...

lint-server:
	cd apps/server && golangci-lint run --timeout 5m --config ../../tooling/lint/golangci.yml

restart-server: build-server
	@echo "→ Stopping server..."
	@lsof -ti :8889 | xargs kill -9 2>/dev/null; sleep 1
	@echo "→ Starting server..."
	@NATIVESTREAM_CONFIG=~/.config/nativestream/config.dev.yaml $(SERVER_BIN) >> /tmp/nativestream.log 2>> /tmp/nativestream-error.log &
	@sleep 1 && echo "✓ Restarted"
	tail -f /tmp/nativestream.log /tmp/nativestream-error.log
logs:
	tail -f /tmp/nativestream.log /tmp/nativestream-error.log

# ── Mac App ───────────────────────────────────────────────────────────────────
APP_DIR     := apps/macos/NativeStream
SCHEME      := NativeStream
DERIVED     := $(APP_DIR)/DerivedData

build-mac:
	@echo "→ Building Mac app (Debug)..."
	xcodebuild -project $(APP_DIR)/NativeStream.xcodeproj \
	           -scheme $(SCHEME) \
	           -configuration Debug \
	           -derivedDataPath $(DERIVED) \
	           build | xcbeautify
	@echo "→ Stripping extended attributes..."
	xattr -cr $(DERIVED)/Build/Products/Debug/NativeStream.app
	@echo "✓ App built"

run-mac: build-mac
	@echo "→ Launching NativeStream..."
	open $(DERIVED)/Build/Products/Debug/NativeStream.app

lint-mac:
	swiftlint lint --config tooling/lint/swiftlint.yml

test-mac-unit:
	@echo "→ Running macOS unit tests..."
	set -o pipefail && xcodebuild -project $(APP_DIR)/NativeStream.xcodeproj \
	           -scheme $(SCHEME) \
	           -derivedDataPath $(DERIVED) \
	           -only-testing:NativeStreamTests \
	           test | xcbeautify

test-mac-ui:
	@echo "→ Running macOS UI tests..."
	set -o pipefail && xcodebuild -project $(APP_DIR)/NativeStream.xcodeproj \
	           -scheme $(SCHEME) \
	           -derivedDataPath $(DERIVED) \
	           -destination 'platform=macOS' \
	           -only-testing:NativeStreamUITests \
	           test | xcbeautify

test-mac: test-mac-unit test-mac-ui

# ── Android App ───────────────────────────────────────────────────────────────
ANDROID_DIR := apps/android

build-android:
	cd $(ANDROID_DIR) && ./gradlew assembleDebug

test-android-unit:
	@echo "→ Running Android local unit and integration tests..."
	cd $(ANDROID_DIR) && ./gradlew testDebugUnitTest

test-android-ui:
	@echo "→ Running Android instrumented Compose UI tests (Requires Emulator/Device)..."
	cd $(ANDROID_DIR) && ./gradlew connectedDebugAndroidTest

test-android-all: test-android-unit test-android-ui

lint-android:
	cd $(ANDROID_DIR) && ./gradlew lint


# ── Dev (server + mac together; Android is run from Android Studio) ───────────
dev: build-server
	@echo "→ Starting server in background..."
	NATIVESTREAM_CONFIG=~/.config/nativestream/config.dev.yaml $(SERVER_BIN) &
	@echo "→ Launching Mac app..."
	$(MAKE) run-mac

# ── Docker ────────────────────────────────────────────────────────────────────
VERSION := $(shell cat apps/server/VERSION)

docker-build:
	docker build -t nativestream-server:$(VERSION) -t nativestream-server:latest .
 
docker-run:
	docker-compose up -d
 
docker-stop:
	docker-compose down
 
docker-logs:
	docker-compose logs -f server
 
docker-test: docker-build
	docker run --rm -d -e NATIVESTREAM_DOCKER=1 -p 8888:8888 --name ns-test nativestream-server:$(VERSION)
	sleep 3
	curl -sf http://localhost:8888/api/health && echo "✓ health OK" || true
	docker stop ns-test

# ── Release ───────────────────────────────────────────────────────────────────
release-binaries:
	@echo "→ Building release binaries v$(VERSION)"
	@mkdir -p dist
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build -C apps/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-darwin-arm64 ./cmd/
	CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build -C apps/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-darwin-amd64 ./cmd/
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -C apps/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-linux-amd64 ./cmd/
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -C apps/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-linux-arm64 ./cmd/
	cd dist && shasum -a 256 nativestream-server-* > checksums.txt
	@echo "✓ dist/ ready — $(VERSION)"

# One target per component per bump type, wired to release.sh.
# e.g. `make release-macos-patch` → ./release.sh macos patch
SCRIPTS_DIR := tooling/scripts

release-server-patch:
	cd $(SCRIPTS_DIR) && ./release.sh server patch
release-server-minor:
	cd $(SCRIPTS_DIR) && ./release.sh server minor
release-server-major:
	cd $(SCRIPTS_DIR) && ./release.sh server major
release-server-current:
	cd $(SCRIPTS_DIR) && ./release.sh server current

release-android-patch:
	cd $(SCRIPTS_DIR) && ./release.sh android patch
release-android-minor:
	cd $(SCRIPTS_DIR) && ./release.sh android minor
release-android-major:
	cd $(SCRIPTS_DIR) && ./release.sh android major
release-android-current:
	cd $(SCRIPTS_DIR) && ./release.sh android current

release-macos-patch:
	cd $(SCRIPTS_DIR) && ./release.sh macos patch
release-macos-minor:
	cd $(SCRIPTS_DIR) && ./release.sh macos minor
release-macos-major:
	cd $(SCRIPTS_DIR) && ./release.sh macos major
release-macos-current:
	cd $(SCRIPTS_DIR) && ./release.sh macos current

# ── Tokens ────────────────────────────────────────────────────────────
create-token: build-server
	@if [ -z "$(LABEL)" ]; then echo "usage: make create-token LABEL=<label>"; exit 1; fi
	NATIVESTREAM_CONFIG=~/.config/nativestream/config.dev.yaml $(SERVER_BIN) --create-token $(LABEL)

list-tokens: build-server
	NATIVESTREAM_CONFIG=~/.config/nativestream/config.dev.yaml $(SERVER_BIN) --list-tokens

revoke-token: build-server
	@if [ -z "$(LABEL)" ]; then echo "usage: make revoke-token LABEL=<label>"; exit 1; fi
	NATIVESTREAM_CONFIG=~/.config/nativestream/config.dev.yaml $(SERVER_BIN) --revoke-token $(LABEL)

# ── Service (macOS) ───────────────────────────────────────────────────
install-service: build-server
	@echo "→ Installing launchd service..."
	sudo cp $(SERVER_BIN) /usr/local/bin/nativestream-server
	$(SERVER_BIN) --install-service
	@echo "✓ Service installed. Server will start on next login."

uninstall-service:
	/usr/local/bin/nativestream-server --uninstall-service
	sudo rm -f /usr/local/bin/nativestream-server
	@echo "✓ Service removed"

# ── Coverage ──────────────────────────────────────────────────────────────────
SCRIPTS_DIR := tooling/scripts

coverage-server:
	@chmod +x $(SCRIPTS_DIR)/coverage.sh
	@$(SCRIPTS_DIR)/coverage.sh server

coverage-android:
	@chmod +x $(SCRIPTS_DIR)/coverage.sh
	@$(SCRIPTS_DIR)/coverage.sh android

coverage-mac:
	@chmod +x $(SCRIPTS_DIR)/coverage.sh
	@$(SCRIPTS_DIR)/coverage.sh macos

coverage-all:
	@chmod +x $(SCRIPTS_DIR)/coverage.sh
	@$(SCRIPTS_DIR)/coverage.sh all

# ── Cleanup ───────────────────────────────────────────────────────────────────
clean:
	@echo "→ Cleaning..."
	rm -f $(SERVER_BIN)
	rm -rf $(DERIVED)
	rm -rf coverage/
	@echo "✓ Clean"

# ── all ─────────────────────────────────────────────────────────────────
build: build-server build-android build-mac
lint : lint-mac lint-server lint-android
test: test-android-unit test-mac-unit test-server
