.PHONY: build-server run-server clean build-app run-app dev test-server test-android-unit test-android-ui test-android-all \
        release-server-patch release-server-minor release-server-major release-server-current \
        release-android-patch release-android-minor release-android-major release-android-current \
        release-macos-patch release-macos-minor release-macos-major release-macos-current


VERSION := $(shell cat VERSION)

# ── Go Server ─────────────────────────────────────────────────────────────────
SERVER_DIR := app/server
SERVER_BIN := $(SERVER_DIR)/nativestream-server

build-server:
	@echo "→ Building NativeStream Server..."
	cd $(SERVER_DIR) && go build -o nativestream-server ./cmd/
	@echo "✓ Binary: $(SERVER_BIN)"

run-server: build-server
	@echo "→ Starting server on http://127.0.0.1:8888"
	$(SERVER_BIN)

test-server:
	@echo "→ Running Go tests..."
	cd $(SERVER_DIR) && go test -race -v ./...

vet-server:
	cd $(SERVER_DIR) && go vet ./...

lint-server:
	cd app/server && golangci-lint run --timeout 5m

restart-server: build-server
	@echo "→ Stopping server..."
	@lsof -ti :8888 | xargs kill -9 2>/dev/null; sleep 1
	@echo "→ Starting server..."
	@$(SERVER_BIN) >> /tmp/nativestream.log 2>> /tmp/nativestream-error.log &
	@sleep 1 && echo "✓ Restarted"
	tail -f /tmp/nativestream.log /tmp/nativestream-error.log
logs:
	tail -f /tmp/nativestream.log /tmp/nativestream-error.log

# ── Mac App ───────────────────────────────────────────────────────────────────
APP_DIR     := app/macos/NativeStream
SCHEME      := NativeStream
DERIVED     := $(APP_DIR)/DerivedData

build-app:
	@echo "→ Building Mac app (Release)..."
	xcodebuild -project $(APP_DIR)/NativeStream.xcodeproj \
	           -scheme $(SCHEME) \
	           -configuration Release \
	           -derivedDataPath $(DERIVED) \
	           build
	@echo "→ Stripping extended attributes..."
	xattr -cr $(DERIVED)/Build/Products/Release/NativeStream.app
	@echo "✓ App built"

run-app:
	@echo "→ Building Mac app (Debug)..."
	xcodebuild -project $(APP_DIR)/NativeStream.xcodeproj \
	           -scheme $(SCHEME) \
	           -configuration Debug \
	           -derivedDataPath $(DERIVED) \
	           build
	@echo "→ Launching NativeStream..."
	open $(DERIVED)/Build/Products/Debug/NativeStream.app

lint-client:
	swiftlint lint --path app/macos/NativeStream

# ── Android App ───────────────────────────────────────────────────────────────
ANDROID_DIR := app/android

test-android-unit:
	@echo "→ Running Android local unit and integration tests..."
	cd $(ANDROID_DIR) && ./gradlew testDebugUnitTest

test-android-ui:
	@echo "→ Running Android instrumented Compose UI tests (Requires Emulator/Device)..."
	cd $(ANDROID_DIR) && ./gradlew connectedDebugAndroidTest

test-android-all: test-android-unit test-android-ui

lint-android:
	cd $(ANDROID_DIR) && ./gradlew lint


# ── Dev (server + app together) ───────────────────────────────────────────────
dev:
	@echo "→ Starting server in background..."
	$(MAKE) build-server
	$(SERVER_BIN) &
	@echo "→ Launching app..."
	$(MAKE) run-app

# ── Docker ────────────────────────────────────────────────────────────────────
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
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build -C app/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-darwin-arm64 ./cmd/
	CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build -C app/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-darwin-amd64 ./cmd/
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -C app/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-linux-amd64 ./cmd/
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -C app/server \
		-ldflags="-s -w -X main.version=$(VERSION)" \
		-o ../../dist/nativestream-server-linux-arm64 ./cmd/
	cd dist && shasum -a 256 nativestream-server-* > checksums.txt
	@echo "✓ dist/ ready — $(VERSION)"

# One target per component per bump type, wired to release.sh.
# e.g. `make release-macos-patch` → ./release.sh macos patch
release-server-patch:
	./release.sh server patch
release-server-minor:
	./release.sh server minor
release-server-major:
	./release.sh server major
release-server-current:
	./release.sh server current

release-android-patch:
	./release.sh android patch
release-android-minor:
	./release.sh android minor
release-android-major:
	./release.sh android major
release-android-current:
	./release.sh android current

release-macos-patch:
	./release.sh macos patch
release-macos-minor:
	./release.sh macos minor
release-macos-major:
	./release.sh macos major
release-macos-current:
	./release.sh macos current

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

# ── Cleanup ───────────────────────────────────────────────────────────────────
clean:
	@echo "→ Cleaning..."
	rm -f $(SERVER_BIN)
	rm -rf $(DERIVED)
	@echo "✓ Clean"