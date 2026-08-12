// ConnectionStateDeciderTests
// Unit tests for ConnectionStateDecider.decide — the pure health/playlist/epg
// result interpretation extracted from ServerHealthViewModel.checkConnection.
// No APIClient involved; every case is driven entirely by the input values.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
import SdkGenSwift

@testable import NativeStream

final class ConnectionStateDeciderTests: XCTestCase {

    private func health(channels: Int = 10, healthy: Int = 8) -> Stream_V1_HealthResponse {
        var response = Stream_V1_HealthResponse()
        response.channels = Int32(channels)
        response.healthy = Int32(healthy)
        return response
    }

    // MARK: No health response

    func testNilHealthProducesUnreachableFailure() {
        let state = ConnectionStateDecider.decide(health: nil, playlist: Data("m3u".utf8), epg: nil)

        XCTAssertEqual(state, .failure(.unreachable))
    }

    func testNilHealthProducesUnreachableRegardlessOfOtherInputs() {
        let state = ConnectionStateDecider.decide(health: nil, playlist: nil, epg: Data("xml".utf8))

        XCTAssertEqual(state, .failure(.unreachable))
    }

    // MARK: Health present, playlist missing/empty

    func testHealthPresentButNilPlaylistProducesNoPlaylistFailure() {
        let state = ConnectionStateDecider.decide(health: health(), playlist: nil, epg: nil)

        XCTAssertEqual(state, .failure(.noPlaylist))
    }

    func testHealthPresentButEmptyPlaylistProducesNoPlaylistFailure() {
        let state = ConnectionStateDecider.decide(health: health(), playlist: Data(), epg: nil)

        XCTAssertEqual(state, .failure(.noPlaylist))
    }

    // MARK: Success cases

    func testHealthAndNonEmptyPlaylistWithNilEpgProducesSuccessWithHasEpgFalse() {
        let state = ConnectionStateDecider.decide(
            health: health(channels: 10, healthy: 8),
            playlist: Data("m3u content".utf8),
            epg: nil
        )

        XCTAssertEqual(state, .success(channels: 10, healthy: 8, hasEpg: false, epgFromPlaylist: false))
    }

    func testHealthAndNonEmptyPlaylistWithEmptyEpgProducesSuccessWithHasEpgFalse() {
        let state = ConnectionStateDecider.decide(
            health: health(),
            playlist: Data("m3u content".utf8),
            epg: Data()
        )

        XCTAssertFalse(state.asSuccess?.hasEpg ?? true)
    }

    func testHealthAndNonEmptyPlaylistWithNonEmptyEpgProducesSuccessWithHasEpgTrue() {
        let state = ConnectionStateDecider.decide(
            health: health(),
            playlist: Data("m3u content".utf8),
            epg: Data("xml content".utf8)
        )

        XCTAssertTrue(state.asSuccess?.hasEpg ?? false)
    }

    func testSuccessCarriesChannelsAndHealthyCountsFromHealthResponse() {
        let state = ConnectionStateDecider.decide(
            health: health(channels: 42, healthy: 40),
            playlist: Data("m3u".utf8),
            epg: nil
        )

        XCTAssertEqual(state.asSuccess?.channels, 42)
        XCTAssertEqual(state.asSuccess?.healthy, 40)
    }

    func testSuccessAlwaysReportsEpgFromPlaylistFalse() {
        // Matches current implementation, which always passes epgFromPlaylist: false.
        let state = ConnectionStateDecider.decide(
            health: health(),
            playlist: Data("m3u".utf8),
            epg: Data("xml".utf8)
        )

        XCTAssertEqual(state.asSuccess?.epgFromPlaylist, false)
    }
}
