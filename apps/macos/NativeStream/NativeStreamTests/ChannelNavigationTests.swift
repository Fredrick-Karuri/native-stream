// ChannelNavigationTests
// Unit tests for ChannelNavigation's next/previous channel selection,
// extracted from MediaKeyHandler for testability without MPRemoteCommandCenter.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class ChannelNavigationTests: XCTestCase {

    private func channel(_ id: String) -> Channel {
        Channel(tvgId: id, name: id, streamURL: URL(string: "http://example.com/\(id).m3u8")!)
    }

    // MARK: nextChannel

    func testNextChannelReturnsNilForEmptyList() {
        XCTAssertNil(ChannelNavigation.nextChannel(after: nil, in: []))
    }

    func testNextChannelReturnsFirstWhenNoCurrentChannel() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]

        let next = ChannelNavigation.nextChannel(after: nil, in: channels)

        XCTAssertEqual(next?.id, "bbc1")
    }

    func testNextChannelAdvancesToFollowingChannel() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]

        let next = ChannelNavigation.nextChannel(after: channels[0], in: channels)

        XCTAssertEqual(next?.id, "itv1")
    }

    func testNextChannelWrapsAroundFromLastToFirst() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]

        let next = ChannelNavigation.nextChannel(after: channels.last, in: channels)

        XCTAssertEqual(next?.id, "bbc1")
    }

    func testNextChannelReturnsFirstWhenCurrentChannelNotInList() {
        let channels = [channel("bbc1"), channel("itv1")]
        let removedChannel = channel("channel5")

        let next = ChannelNavigation.nextChannel(after: removedChannel, in: channels)

        XCTAssertEqual(next?.id, "bbc1")
    }

    func testNextChannelWithSingleChannelListReturnsSameChannel() {
        let channels = [channel("bbc1")]

        let next = ChannelNavigation.nextChannel(after: channels[0], in: channels)

        XCTAssertEqual(next?.id, "bbc1")
    }

    // MARK: previousChannel

    func testPreviousChannelReturnsNilForEmptyList() {
        XCTAssertNil(ChannelNavigation.previousChannel(before: nil, in: []))
    }

    func testPreviousChannelReturnsLastWhenNoCurrentChannel() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]

        let prev = ChannelNavigation.previousChannel(before: nil, in: channels)

        XCTAssertEqual(prev?.id, "channel4")
    }

    func testPreviousChannelMovesToPrecedingChannel() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]

        let prev = ChannelNavigation.previousChannel(before: channels[2], in: channels)

        XCTAssertEqual(prev?.id, "itv1")
    }

    func testPreviousChannelWrapsAroundFromFirstToLast() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]

        let prev = ChannelNavigation.previousChannel(before: channels.first, in: channels)

        XCTAssertEqual(prev?.id, "channel4")
    }

    func testPreviousChannelReturnsLastWhenCurrentChannelNotInList() {
        let channels = [channel("bbc1"), channel("itv1")]
        let removedChannel = channel("channel5")

        let prev = ChannelNavigation.previousChannel(before: removedChannel, in: channels)

        XCTAssertEqual(prev?.id, "itv1")
    }

    func testPreviousChannelWithSingleChannelListReturnsSameChannel() {
        let channels = [channel("bbc1")]

        let prev = ChannelNavigation.previousChannel(before: channels[0], in: channels)

        XCTAssertEqual(prev?.id, "bbc1")
    }

    // MARK: Round-trip sanity

    func testNextThenPreviousReturnsToOriginalChannel() {
        let channels = [channel("bbc1"), channel("itv1"), channel("channel4")]
        let start = channels[1]

        let next = ChannelNavigation.nextChannel(after: start, in: channels)
        let backToStart = ChannelNavigation.previousChannel(before: next, in: channels)

        XCTAssertEqual(backToStart?.id, start.id)
    }
}
