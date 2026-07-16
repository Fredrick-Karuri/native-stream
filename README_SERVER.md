# NativeStream Server

The content orchestration engine behind NativeStream. It manages content ingestion, stream reliability, metadata, delivery APIs, and the Local Media Connect control plane, so every client has a single, always-healthy source to poll.

## What You Can Do

- Organize and manage sports channels and content sources
- Get continuous stream health monitoring with automatic failover to a healthier playback path
- Generate an electronic programme guide (EPG) with match schedules
- Auto-discover new stream links from configured sources and match them to your channels
- Serve standardized playlists, EPG data, and a proxy endpoint to any client
- Broker real-time control messages between devices on your local network (Local Media Connect)

## Quick Start

### Homebrew (recommended)

```bash
brew tap yourname/nativestream
brew install nativestream-server
brew services start nativestream-server
```

The server starts automatically on login.

### Build from source

```bash
git clone https://github.com/yourname/nativestream.git
cd nativestream
make build-server
make run-server
```

### Verify it's running

```bash
curl http://localhost:8888/api/health
```

For the full endpoint reference, see [docs/api.md](docs/api.md). For configuration options, see [docs/configuration.md](docs/configuration.md). For architecture and how stream self-healing and discovery work internally, see [docs/architecture.md](docs/architecture.md).

## Requirements

| Requirement | Version/Detail |
|---|---|
| Go | 1.22+ |
| OS | macOS (Homebrew tap + launchd service) |
| Runtime dependencies | None beyond the Go standard library |

## Running as a Service

```bash
make install-service
```

Remove the service:

```bash
make uninstall-service
```

Dev server (port 8889, separate config):

```bash
make server-dev
```