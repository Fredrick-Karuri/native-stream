// EPGStoreTests
// Unit tests for EPGStore's query surface: current/next programme resolution,
// schedule windowing, case-insensitive channel id fallback, and match-rate diagnostics.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class EPGStoreTests: XCTestCase {

    // MARK: Helpers

    private let now = Date()

    private func date(_ hourOffset: TimeInterval) -> Date {
        now.addingTimeInterval(hourOffset * 3600)
    }

    private func programme(
        channelId: String,
        title: String,
        startHourOffset: TimeInterval,
        stopHourOffset: TimeInterval
    ) -> Programme {
        Programme(
            channelId: channelId,
            title: title,
            start: date(startHourOffset),
            stop: date(stopHourOffset)
        )
    }

    private func channel(tvgId: String) -> Channel {
        Channel(tvgId: tvgId, name: tvgId, streamURL: URL(string: "http://example.com/\(tvgId).m3u8")!)
    }

    // MARK: currentProgramme

    func testCurrentProgrammeReturnsTheOneAiringNow() {
        let nowShowing = programme(channelId: "bbc1", title: "Now Showing", startHourOffset: -1, stopHourOffset: 1)
        let ended = programme(channelId: "bbc1", title: "Ended", startHourOffset: -3, stopHourOffset: -1)
        let store = EPGStore(programmes: ["bbc1": [ended, nowShowing]])

        XCTAssertEqual(store.currentProgramme(for: "bbc1")?.title, "Now Showing")
    }

    func testCurrentProgrammeReturnsNilWhenNothingIsAiring() {
        let past = programme(channelId: "bbc1", title: "Past", startHourOffset: -3, stopHourOffset: -1)
        let future = programme(channelId: "bbc1", title: "Future", startHourOffset: 1, stopHourOffset: 2)
        let store = EPGStore(programmes: ["bbc1": [past, future]])

        XCTAssertNil(store.currentProgramme(for: "bbc1"))
    }

    func testCurrentProgrammeReturnsNilForUnknownChannel() {
        let store = EPGStore(programmes: [:])

        XCTAssertNil(store.currentProgramme(for: "missing"))
    }

    // MARK: nextProgramme

    func testNextProgrammeReturnsEarliestFutureProgramme() {
        let later = programme(channelId: "bbc1", title: "Later", startHourOffset: 3, stopHourOffset: 4)
        let soonest = programme(channelId: "bbc1", title: "Soonest", startHourOffset: 1, stopHourOffset: 2)
        let store = EPGStore(programmes: ["bbc1": [later, soonest]])

        XCTAssertEqual(store.nextProgramme(for: "bbc1")?.title, "Soonest")
    }

    func testNextProgrammeIgnoresCurrentlyAiringProgramme() {
        let nowShowing = programme(channelId: "bbc1", title: "Now", startHourOffset: -1, stopHourOffset: 1)
        let upcoming = programme(channelId: "bbc1", title: "Upcoming", startHourOffset: 2, stopHourOffset: 3)
        let store = EPGStore(programmes: ["bbc1": [nowShowing, upcoming]])

        XCTAssertEqual(store.nextProgramme(for: "bbc1")?.title, "Upcoming")
    }

    func testNextProgrammeReturnsNilWhenNoFutureProgrammesExist() {
        let past = programme(channelId: "bbc1", title: "Past", startHourOffset: -3, stopHourOffset: -1)
        let store = EPGStore(programmes: ["bbc1": [past]])

        XCTAssertNil(store.nextProgramme(for: "bbc1"))
    }

    // MARK: schedule(for:)

    func testScheduleReturnsAllProgrammesSortedByStart() {
        let second = programme(channelId: "bbc1", title: "Second", startHourOffset: 2, stopHourOffset: 3)
        let first = programme(channelId: "bbc1", title: "First", startHourOffset: -1, stopHourOffset: 1)
        let store = EPGStore(programmes: ["bbc1": [second, first]])

        XCTAssertEqual(store.schedule(for: "bbc1").map(\.title), ["First", "Second"])
    }

    func testScheduleReturnsEmptyArrayForUnknownChannel() {
        let store = EPGStore(programmes: [:])

        XCTAssertTrue(store.schedule(for: "missing").isEmpty)
    }

    // MARK: schedule(for:from:to:)

    func testScheduleWithRangeFiltersToWindowAndSorts() {
        let before = programme(channelId: "bbc1", title: "Before", startHourOffset: -5, stopHourOffset: -4)
        let inWindowLate = programme(channelId: "bbc1", title: "InWindowLate", startHourOffset: 2, stopHourOffset: 3)
        let inWindowEarly = programme(channelId: "bbc1", title: "InWindowEarly", startHourOffset: 1, stopHourOffset: 2)
        let after = programme(channelId: "bbc1", title: "After", startHourOffset: 10, stopHourOffset: 11)
        let store = EPGStore(programmes: ["bbc1": [before, inWindowLate, inWindowEarly, after]])

        let result = store.schedule(for: "bbc1", from: date(0), to: date(5))

        XCTAssertEqual(result.map(\.title), ["InWindowEarly", "InWindowLate"])
    }

    func testScheduleWithRangeExcludesProgrammeStartingExactlyAtToBoundary() {
        let atBoundary = programme(channelId: "bbc1", title: "AtBoundary", startHourOffset: 5, stopHourOffset: 6)
        let store = EPGStore(programmes: ["bbc1": [atBoundary]])

        let result = store.schedule(for: "bbc1", from: date(0), to: date(5))

        XCTAssertTrue(result.isEmpty)
    }

    func testScheduleWithRangeIncludesProgrammeStartingExactlyAtFromBoundary() {
        let atBoundary = programme(channelId: "bbc1", title: "AtBoundary", startHourOffset: 0, stopHourOffset: 1)
        let store = EPGStore(programmes: ["bbc1": [atBoundary]])

        let result = store.schedule(for: "bbc1", from: date(0), to: date(5))

        XCTAssertEqual(result.map(\.title), ["AtBoundary"])
    }

    // MARK: Case-insensitive channel id fallback (FX-002)

    func testResolveFallsBackToCaseInsensitiveMatchWhenExactMatchMissing() {
        let show = programme(channelId: "BBC1.uk", title: "Exact Case Stored", startHourOffset: -1, stopHourOffset: 1)
        let store = EPGStore(programmes: ["BBC1.uk": [show]])

        XCTAssertEqual(store.currentProgramme(for: "bbc1.uk")?.title, "Exact Case Stored")
    }

    func testResolveUsesExactMatchWithoutNeedingCaseInsensitiveFallback() {
        let exact = programme(channelId: "bbc1.uk", title: "Exact", startHourOffset: -1, stopHourOffset: 1)
        let store = EPGStore(programmes: ["bbc1.uk": [exact]])

        XCTAssertEqual(store.currentProgramme(for: "bbc1.uk")?.title, "Exact")
    }

    // MARK: matchRate

    func testMatchRateReturnsZeroForEmptyChannelList() {
        let store = EPGStore(programmes: [:])

        XCTAssertEqual(store.matchRate(for: []), 0)
    }

    func testMatchRateReturnsOneWhenAllChannelsHaveMatchingProgrammes() {
        let show = programme(channelId: "bbc1", title: "Show", startHourOffset: -1, stopHourOffset: 1)
        let store = EPGStore(programmes: ["bbc1": [show]])

        XCTAssertEqual(store.matchRate(for: [channel(tvgId: "bbc1")]), 1)
    }

    func testMatchRateReturnsFractionWhenSomeChannelsAreUnmatched() {
        let show = programme(channelId: "bbc1", title: "Show", startHourOffset: -1, stopHourOffset: 1)
        let store = EPGStore(programmes: ["bbc1": [show]])

        let rate = store.matchRate(for: [channel(tvgId: "bbc1"), channel(tvgId: "unmatched")])

        XCTAssertEqual(rate, 0.5)
    }

    func testMatchRateUsesCaseInsensitiveFallbackForMatching() {
        let show = programme(channelId: "BBC1", title: "Show", startHourOffset: -1, stopHourOffset: 1)
        let store = EPGStore(programmes: ["BBC1": [show]])

        XCTAssertEqual(store.matchRate(for: [channel(tvgId: "bbc1")]), 1)
    }

    // MARK: Diagnostics counts

    func testChannelCountReflectsNumberOfDistinctChannelKeys() {
        let store = EPGStore(programmes: [
            "bbc1": [programme(channelId: "bbc1", title: "A", startHourOffset: 0, stopHourOffset: 1)],
            "itv1": [programme(channelId: "itv1", title: "B", startHourOffset: 0, stopHourOffset: 1)]
        ])

        XCTAssertEqual(store.channelCount, 2)
    }

    func testProgrammeCountSumsAcrossAllChannels() {
        let store = EPGStore(programmes: [
            "bbc1": [
                programme(channelId: "bbc1", title: "A", startHourOffset: 0, stopHourOffset: 1),
                programme(channelId: "bbc1", title: "B", startHourOffset: 2, stopHourOffset: 3)
            ],
            "itv1": [programme(channelId: "itv1", title: "C", startHourOffset: 0, stopHourOffset: 1)]
        ])

        XCTAssertEqual(store.programmeCount, 3)
    }

    func testKnownChannelIdsReturnsAllStoredKeys() {
        let store = EPGStore(programmes: [
            "bbc1": [programme(channelId: "bbc1", title: "A", startHourOffset: 0, stopHourOffset: 1)],
            "itv1": [programme(channelId: "itv1", title: "B", startHourOffset: 0, stopHourOffset: 1)]
        ])

        XCTAssertEqual(store.knownChannelIds, ["bbc1", "itv1"])
    }
}
