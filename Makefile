.PHONY: build-server run-server clean build-app test-server

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

# ── Mac App ───────────────────────────────────────────────────────────────────
APP_DIR     := app/macos
SCHEME      := NativeStreamMac
DERIVED     := $(APP_DIR)/DerivedData

build-app:
	@echo "→ Building Mac app (Release)..."
	xcodebuild -project $(APP_DIR)/NativeStreamMac.xcodeproj \
	           -scheme $(SCHEME) \
	           -configuration Release \
	           -derivedDataPath $(DERIVED) \
	           build
	@echo "✓ App built"

# ── Cleanup ───────────────────────────────────────────────────────────────────
clean:
	@echo "→ Cleaning..."
	rm -f $(SERVER_BIN)
	rm -rf $(DERIVED)
	@echo "✓ Clean"

# ── Install service (macOS) ───────────────────────────────────────────────────
install-service: build-server
	@echo "→ Installing launchd service..."
	cp $(SERVER_BIN) /usr/local/bin/nativestream-server
	$(SERVER_BIN) --install-service
	@echo "✓ Service installed. Server will start on next login."

uninstall-service:
	/usr/local/bin/nativestream-server --uninstall-service
	rm -f /usr/local/bin/nativestream-server
	@echo "✓ Service removed"