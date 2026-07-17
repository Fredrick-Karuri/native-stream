# Development

How to build, run, test, and lint each component locally. For how to cut a release, see [releasing.md](releasing.md). For system design, see [architecture.md](architecture.md), [server-architecture.md](server-architecture.md), [mac-architecture.md](mac-architecture.md), and [android-architecture.md](android-architecture.md).

## Command Runner: ordo

The day-to-day workflow uses [ordo](https://github.com/) — a structured command runner, a cleaner alternative to `make`. It reads `ordo.yaml` at the repo root and groups commands as `group:command`.

```bash
ordo server:dev              # run server with dev config
ordo server:build            # build the server binary
ordo server:run              # build and run
ordo server:test             # go test -race -v ./...
ordo server:release-patch    # bump patch version, tag, push
```

A `Makefile` with equivalent targets also exists at the repo root (e.g. `make build-server` does the same thing as `ordo server:build`) for anyone without `ordo` installed. The two are kept in sync manually — if you add an `ordo.yaml` command, add the matching `Makefile` target too, and vice versa.

### Server (`ordo server:*` / `make *-server`)

| ordo | make | Does |
|---|---|---|
| `server:dev` | `server-dev` | Run with `~/.config/nativestream/config.dev.yaml`, port 8889 |
| `server:build` | `build-server` | `go build -o nativestream-server ./cmd/` |
| `server:run` | `run-server` | Build, then start on `127.0.0.1:8888` |
| `server:test` | `test-server` | `go test -race -v ./...` |
| `server:vet` | `vet-server` | `go vet ./...` |
| — | `lint-server` | `golangci-lint run --timeout 5m` |
| `server:restart` | `restart-server` | Kill anything on :8888, rebuild, restart in background, tail logs |
| `server:logs` | `logs` | Tail `/tmp/nativestream.log` and `/tmp/nativestream-error.log` |

### Mac (`ordo mac:*` / `make *-app`)

| ordo | make | Does |
|---|---|---|
| `mac:build` | `build-app` | `xcodebuild` Release, then strips extended attributes (`xattr -cr`) so the app runs without a Gatekeeper quarantine prompt on the build machine |
| `mac:run` | `run-app` | `xcodebuild` Debug, then `open`s the built app |
| `mac:lint` | `lint-client` | `swiftlint lint --path app/macos/NativeStream` |

### Android (`ordo android:*` / `make *-android`)

| ordo | make | Does |
|---|---|---|
| `android:test-unit` | `test-android-unit` | `./gradlew testDebugUnitTest` |
| `android:test-ui` | `test-android-ui` | `./gradlew connectedDebugAndroidTest` — requires an emulator or device |
| `android:test-all` | `test-android-all` | Both of the above |
| `android:lint` | `lint-android` | `./gradlew lint` |

### Combined dev environment

| ordo | make | Does |
|---|---|---|
| `dev:start` | `dev` | Build + background-start the server, then build + launch the Mac app Debug build |

### Service management (macOS)

| ordo | make | Does |
|---|---|---|
| `service:install` | `install-service` | Build, copy binary to `/usr/local/bin`, install as a launchd service (starts on login) |
| `service:uninstall` | `uninstall-service` | Reverse of the above |

### Cleanup

| ordo | make | Does |
|---|---|---|
| `clean:all` | `clean` | Remove the server binary and Xcode `DerivedData` |

---

## Docker (work in progress — unverified)

A `Dockerfile` and `docker-compose.yml` exist for running the server in a container, but **this path hasn't actually been run yet** — treat everything below as unverified until someone confirms it works end to end.

```bash
make docker-build   # or: ordo (no ordo target defined for this yet)
make docker-run      # docker-compose up -d
make docker-logs     # docker-compose logs -f server
make docker-stop     # docker-compose down
```

What it does, from reading the Dockerfile and compose file:

- Two-stage build: `golang:1.25-alpine` compiles a static binary, then it's copied into a plain `alpine:3.19` runtime image with `ca-certificates` and `tzdata` (needed for HTTPS to GitHub/Reddit/ESPN and for EPG timestamp handling).
- Runs as a non-root `nativestream` user.
- Config is expected to be bind-mounted read-only at `/config/config.yaml`; channel/EPG state persists to a named volume at `/data`.
- The compose file binds the container's port 8888 to `127.0.0.1` only on the host — consistent with the server's own localhost-only binding described in [server-architecture.md](server-architecture.md#http-middleware-stack).
- A healthcheck hits `/api/health` every 30s.

Open questions before this can be called supported: has it been built and actually started successfully; does `/config/config.yaml` need to exist on the host before first run or does the container create a default; and how `NATIVESTREAM_DATA`/`NATIVESTREAM_CONFIG` env vars are consumed by the Go binary isn't confirmed from the compose file alone. `make docker-test` (build, start, curl `/api/health`, stop) is the fastest way to sanity-check this — worth running before writing this up as a supported install path in [README_SERVER.md](../README_SERVER.md).