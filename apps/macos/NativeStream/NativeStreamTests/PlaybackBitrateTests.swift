// PlaybackBitrateTests
// Unit tests for PlaybackBitrate.preferredPeakBitRate — the pure bitrate-cap
// selection extracted from PlayerViewModel.setQuality. No AVPlayer involved.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class PlaybackBitrateTests: XCTestCase {

    func testAutoReturnsUnlimitedZero() {
        XCTAssertEqual(PlaybackBitrate.preferredPeakBitRate(for: .auto), 0)
    }

    func testLocked1080ReturnsExpectedCap() {
        XCTAssertEqual(PlaybackBitrate.preferredPeakBitRate(for: .locked(height: 1080)), 8_000_000)
    }

    func testLocked720ReturnsExpectedCap() {
        XCTAssertEqual(PlaybackBitrate.preferredPeakBitRate(for: .locked(height: 720)), 4_000_000)
    }

    func testLocked480ReturnsExpectedCap() {
        XCTAssertEqual(PlaybackBitrate.preferredPeakBitRate(for: .locked(height: 480)), 1_500_000)
    }

    func testLockedWithUnknownHeightFallsBackToZero() {
        XCTAssertEqual(PlaybackBitrate.preferredPeakBitRate(for: .locked(height: 360)), 0)
    }

    func testLockedWithZeroHeightFallsBackToZero() {
        XCTAssertEqual(PlaybackBitrate.preferredPeakBitRate(for: .locked(height: 0)), 0)
    }

    func testAllPresetsProduceAResult() {
        // Sanity check: every value in StreamQuality.presets should resolve
        // without crashing and without needing a new case added here later
        // going unnoticed (this would still pass, but documents the set).
        for preset in StreamQuality.presets {
            _ = PlaybackBitrate.preferredPeakBitRate(for: preset)
        }
    }
}
