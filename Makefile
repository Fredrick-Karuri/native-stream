.PHONY: build-server run-server clean build-app run-app dev test-server

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

# ── Dev (server + app together) ───────────────────────────────────────────────
dev:
	@echo "→ Starting server in background..."
	$(MAKE) build-server
	$(SERVER_BIN) &
	@echo "→ Launching app..."
	$(MAKE) run-app

# ── Cleanup ───────────────────────────────────────────────────────────────────
clean:
	@echo "→ Cleaning..."
	rm -f $(SERVER_BIN)
	rm -rf $(DERIVED)
	@echo "✓ Clean"

# ── Install service (macOS) ───────────────────────────────────────────────────
install-service: build-server
	@echo "→ Installing launchd service..."
	sudo cp $(SERVER_BIN) /usr/local/bin/nativestream-server
	$(SERVER_BIN) --install-service
	@echo "✓ Service installed. Server will start on next login."

uninstall-service:
	/usr/local/bin/nativestream-server --uninstall-service
	sudo rm -f /usr/local/bin/nativestream-server
	@echo "✓ Service removed"