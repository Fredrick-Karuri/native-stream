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

### Download a release (recommended)

```bash
curl -L https://github.com/Fredrick-Karuri/native-stream/releases/latest/download/nativestream-server-darwin-arm64.tar.gz | tar -xz
sudo mv nativestream-server-darwin-arm64 /usr/local/bin/nativestream-server
nativestream-server --install-service
```

(Swap the asset name for your platform — `darwin-amd64`, `linux-amd64`, or `linux-arm64` — see [the latest release](https://github.com/Fredrick-Karuri/native-stream/releases) for all options and checksums.)

`--install-service` runs it as a background launchd service that starts on login. Skip that flag to just run it in the foreground instead.

### Build from source

```bash
git clone https://github.com/Fredrick-Karuri/native-stream.git
cd native-stream
ordo server:build && ordo server:run
# or, without ordo installed:
make build-server && make run-server
```

### Verify it's running

```bash
curl http://localhost:8888/api/health
```

For the full endpoint reference, see [docs/api.md](docs/api.md). For configuration options, see [docs/configuration.md](docs/configuration.md). For how stream self-healing and discovery work internally, see [docs/server-architecture.md](docs/server-architecture.md); for the cross-platform system view, see [docs/architecture.md](docs/architecture.md). For all local dev commands (dev server, tests, linting, service install, Docker) see [docs/development.md](docs/development.md). For cutting a release, see [docs/releasing.md](docs/releasing.md).

## Requirements

| Requirement | Version/Detail |
|---|---|
| Go | 1.25 (per the Docker build — confirm this matches the non-Docker build requirement) |
| OS | macOS (prebuilt binary + launchd service) or Linux (prebuilt binary, or Docker — unverified, see [docs/development.md](docs/development.md#docker-work-in-progress--unverified)) |
| Runtime dependencies | None beyond the Go standard library |

## Running as a Service

```bash
ordo service:install   # or: make install-service
```

Remove the service:

```bash
ordo service:uninstall   # or: make uninstall-service
```

Dev server (port 8889, separate config):

```bash
ordo server:dev   # or: make run-server
```

For the full command reference — build, test, lint, restart, logs — see [docs/development.md](docs/development.md).