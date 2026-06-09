# CI/CD Architecture & Release Documentation

This directory contains the GitHub Actions automation workflows for the NativeStream monorepo. The pipelines are structured with strict component isolation to allow the server, macOS client, and Android client to build, test, and release independently.

---

## 🏗 Workflow Directory Structure

```text
.github/workflows/
├── Workflows.md            # This documentation file
├── ci-server.yml           # Verifies server on branch pushes/PRs
├── release-server.yml      # Builds & publishes Server binaries on tags
├── ci-macos.yml            # Verifies macOS application compilation & tests
├── release-macos.yml       # Packages & distributes macOS client app
├── ci-android.yml          # Lints, tests, and compiles Android debug APK
└── release-android.yml     # Compiles production-ready Android distribution APK
```

---

## 🚦 Automation Pipelines

### 1. Verification Pipelines (CI)
These workflows run automatically on `push` and `pull_request` triggers matching changes to specific sub-directories.

* **Server CI (`ci-server.yml`)**
  * Trigger: Changes inside `app/server/**`
  * Execution: Runs formatting checks (`go vet`), executes unit tests with race detection, tracks code coverage statistics, and runs non-production compilation checks.
* **macOS CI (`ci-macos.yml`)**
  * Trigger: Changes inside `app/macos/**`
  * Execution: Executes on a macOS runner, selects the targeted Xcode toolchain version, compiles the application schema inside `app/macos/NativeStream/` in `Release` configuration, and executes the XCTest suite.
* **Android CI (`ci-android.yml`)**
  * Trigger: Changes inside `app/android/**`
  * Execution: Boots an environment with JDK 17, initializes aggressive dependency caching via the Gradle action tool, executes code analysis/linting rules, runs local unit tests, and archives the compiled debug verification APK.

### 2. Distribution Pipelines (Release)
Releases are decoupled across components and are strictly event-driven. They only execute when a uniquely scoped Git tag is pushed to the repository. They ignore standard code path modifications.

| Target Component | Git Tag Blueprint | Active Workflow File | Release Asset Deliverables |
| :--- | :--- | :--- | :--- |
| **Go Server Backend** | `server/v*` (e.g. `server/v1.2.0`) | `release-server.yml` | 4 Server Tarballs (`darwin`/`linux` architectures) + SHA256 Verification Checksums |
| **macOS Desktop Client** | `macos/v*` (e.g. `macos/v1.0.5`) | `release-macos.yml` | Compressed Application Bundle Ready for Local Extraction (`NativeStream-macOS-arm64.zip`) |
| **Android Mobile Client** | `android/v*` (e.g. `android/v1.0.0`) | `release-android.yml` | Production-ready Unsigned Distribution Binary (`NativeStream-Android-v1.0.0.apk`) |

---

## 🚀 How to Publish a Component Release (`release.sh`)

To make deploying updates painless, use the centralized root automation tool **`./release.sh`**. 

This script reads your internal configuration configurations (`VERSION` file, Gradle configurations, or Xcode target settings), handles the code math, auto-increments your tracking build configurations/numbers, commits the configuration updates, creates the scoped Git tag, and pushes to remote repositories automatically.

### Command Anatomy
```bash
./release.sh [component] [bump-type]
```
* **Components**: `server` | `android` | `macos`
* **Bump Types**: `patch` | `minor` | `major` | `current`

### Examples

#### 📱 Deploying an Android Client Update
Increments the `versionCode` integer by `+1`, upgrades your semantic `versionName` patch block (e.g., `1.0.0` ➡️ `1.0.1`), commits the file change, creates an `android/v1.0.1` tag, and pushes to GitHub:
```bash
./release.sh android patch
```

#### 🖥 Deploying a macOS Client Update
Increments the internal project build integer (`CURRENT_PROJECT_VERSION`), increments your user-facing semantic version string (`MARKETING_VERSION`), commits the configuration modifications inside your Xcode project target file, maps an asset tag, and pushes:
```bash
./release.sh macos minor
```

#### 💾 Deploying a Server Update
Bumps the flat `app/server/VERSION` configuration file by an increment layer and triggers the respective delivery flow pipelines:
```bash
./release.sh server patch
```

#### 🔍 Dry-Running / Tagging the Current State
Reads your system configuration file exactly as it stands right now, skips editing any files or committing code modifications, and creates/pushes the exact tag matching your current settings:
```bash
./release.sh android current
```

---

## 🛠 Maintenance & Performance Enhancements

### Log Parsing
Client compilation loops process outputs through `xcpretty` to minimize noise, keeping GitHub Action pipeline logs readable and performant.

### Cache Layering
The Android and Server workflows leverage directory hash mapping keys (`go.sum`, `build.gradle.kts`, `libs.versions.toml`). This prevents the build systems from downloading identical external dependencies on every execution, cutting pipeline runtimes drastically.
