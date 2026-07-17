# Releasing

How a release actually ships, end to end: version bump → git tag → CI build → published asset. For day-to-day dev commands, see [development.md](development.md).

## Versioning is per-component, not monorepo-wide

Server, macOS, and Android each version and release independently — a server release doesn't imply a client release, and vice versa. Each component's version lives in a different place, because each is a different toolchain's native format:

| Component | Source of truth | Format |
|---|---|---|
| Server | `app/server/VERSION` | Flat semver string, e.g. `1.2.0` |
| Android | `app/android/app/build.gradle.kts` — `versionName` + `versionCode` | Semver string + monotonic integer |
| macOS | `app/macos/NativeStream/NativeStream.xcodeproj/project.pbxproj` — `MARKETING_VERSION` + `CURRENT_PROJECT_VERSION` | Semver string + monotonic integer |

## Cutting a release: `release.sh`

```bash
./release.sh [server|android|macos] [patch|minor|major|current]
# or via ordo/make:
ordo server:release-patch      # == make release-server-patch == ./release.sh server patch
```

What it does:

1. Reads the current version from that component's source of truth.
2. If bump type isn't `current`, computes the new semver (patch/minor/major), writes it back to the source file, commits it (`chore(server): bump version to 1.2.1`). Android and macOS also increment their integer build code alongside the semver.
3. `current` skips step 2 entirely — it re-tags whatever version is already committed, useful for re-triggering a release without changing anything.
4. Checks whether the target tag (`{component}/v{version}`) already exists locally or on `origin`. If it does, prompts to delete and retag rather than failing outright — this is how you re-run a release after a bad build.
5. Creates the tag and pushes `main` plus tags to `origin`, which is what triggers CI (see below).

The bump math itself pads a bare `X.Y` version (which Xcode sometimes reports) to `X.Y.0` before incrementing, so `macos current` works even before the project has ever been through a `minor`/`major` bump.

## What happens after the tag is pushed

Releases are tag-triggered and strictly scoped per component — pushing a `server/v*` tag only runs the server release pipeline, never the client ones.

| Component | Tag pattern | Workflow | Publishes |
|---|---|---|---|
| Server | `server/v*` | `release-server.yml` | 4 tarballs (darwin/linux × amd64/arm64) + SHA256 checksums |
| macOS | `macos/v*` | `release-macos.yml` | `NativeStream-macOS-arm64.zip` |
| Android | `android/v*` | `release-android.yml` | `NativeStream-Android-v{version}.apk` (unsigned) |

These map directly to the release badges in the root [README.md](../README.md).

### Server binaries — two build paths

There's also a `make release-binaries` / `ordo` target that cross-compiles the same four server binaries locally into `dist/` with checksums, independent of CI:

```bash
make release-binaries
```

This looks like it exists so a maintainer can produce release artifacts locally (for manual verification, or if CI is down) using the same `-ldflags="-s -w -X main.version=..."` build as the CI pipeline. Whether CI's `release-server.yml` literally shells out to this same recipe or duplicates it isn't confirmed from the Makefile alone — worth checking the workflow file directly if the two ever produce different binaries.

---

## CI (verification, not release)

Separate from the release pipelines above, three CI workflows run on every push/PR touching their respective directory:

| Workflow | Triggers on changes to | Runs |
|---|---|---|
| `ci-server.yml` | `app/server/**` | `go vet`, `go test -race` with coverage, non-production compile check |
| `ci-macos.yml` | `app/macos/**` | Xcode build (Release config) + XCTest suite, on a macOS runner |
| `ci-android.yml` | `app/android/**` | JDK 17 setup, Gradle dependency caching, lint, unit tests, debug APK build |

Client build logs are piped through `xcpretty` to keep output readable. Android and Server workflows cache dependencies keyed on `go.sum` / `build.gradle.kts` / `libs.versions.toml` respectively, to avoid re-downloading unchanged dependencies on every run.

These CI workflows are what the three CI badges at the top of [README.md](../README.md) reflect — distinct from the release badges, which track the tag-triggered pipelines above.