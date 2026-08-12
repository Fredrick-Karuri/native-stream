// NowScreenViewModelTests
// Unit tests for NowScreenViewModel.recompute's three-bucket classification:
// liveMatches (sport + "vs" title), liveOnAir (everything else currently airing),
// and startingSoon (no current programme, next one within the lookahead window).
// Drives a real EPGViewModel by setting `stores` directly — no network mocking
// needed since EPGViewModel's query methods are pure once stores is populated.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

@MainActor
final class NowScreenViewModelTests: XCTestCase {

    private var viewModel: NowScreenViewModel!
    private var epgVM: EPGViewModel!

    override func setUp() async throws {
        try await super.setUp()
        viewModel = NowScreenViewModel()
        epgVM = EPGViewModel()
    }

    override func tearDown() async throws {
        viewModel = nil
        epgVM = nil
        try await super.tearDown()
    }

    // MARK: Helpers

    private func channel(tvgId: String) -> Channel {
        Channel(tvgId: tvgId, name: tvgId, streamURL: URL(string: "http://example.com/\(tvgId).m3u8")!)
    }

    private func programme(
        channelId: String,
        title: String,
        startHourOffset: Double,
        stopHourOffset: Double
    ) -> Programme {
        Programme(
            channelId: channelId,
            title: title,
            start: Date().addingTimeInterval(startHourOffset * 3600),
            stop: Date().addingTimeInterval(stopHourOffset * 3600)
        )
    }

    private func setStores(_ programmes: [String: [Programme]]) {
        epgVM.stores = [UUID(): EPGStore(programmes: programmes)]
    }

    // MARK: liveMatches — sport + "vs" required

    func testLiveMatchesIncludesCurrentSportProgrammeWithVs() {
        let prog = programme(channelId: "bbc1", title: "Premier League: Arsenal vs Chelsea", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [prog]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertEqual(viewModel.liveMatches.map(\.programme.title), ["Premier League: Arsenal vs Chelsea"])
    }

    func testLiveMatchesExcludesSportProgrammeWithoutVs() {
        // Sport keyword present but no " vs " substring — e.g. a highlights show.
        let prog = programme(channelId: "bbc1", title: "Premier League Highlights", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [prog]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.liveMatches.isEmpty)
    }

    func testLiveMatchesExcludesNonSportProgrammeEvenWithVsSubstring() {
        // Contains " vs " but no sport keyword in the title.
        let prog = programme(channelId: "bbc1", title: "Debate Show: Team A vs Team B", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [prog]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.liveMatches.isEmpty)
    }

    // MARK: liveOnAir — currently airing, not a sport match

    func testLiveOnAirIncludesNonSportCurrentProgramme() {
        let prog = programme(channelId: "bbc1", title: "The Evening News", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [prog]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertEqual(viewModel.liveOnAir.map(\.programme.title), ["The Evening News"])
    }

    func testLiveOnAirExcludesSportMatchProgramme() {
        let prog = programme(channelId: "bbc1", title: "Premier League: Arsenal vs Chelsea", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [prog]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.liveOnAir.isEmpty)
    }

    func testLiveOnAirIncludesSportKeywordProgrammeWithoutVs() {
        // isSportMatch is true (keyword present) but no " vs " — so it doesn't
        // qualify for liveMatches, and per recompute's independent condition
        // (!prog.isSportMatch) it also doesn't qualify for liveOnAir. Documents
        // this programme falls into neither bucket.
        let prog = programme(channelId: "bbc1", title: "Premier League Highlights", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [prog]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.liveMatches.isEmpty)
        XCTAssertTrue(viewModel.liveOnAir.isEmpty)
    }

    // MARK: startingSoon — nothing airing now, next within lookahead

    func testStartingSoonIncludesProgrammeWithinTwoHourLookahead() {
        let upcoming = programme(channelId: "bbc1", title: "Upcoming Show", startHourOffset: 1, stopHourOffset: 2)
        setStores(["bbc1": [upcoming]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertEqual(viewModel.startingSoon.map(\.programme.title), ["Upcoming Show"])
    }

    func testStartingSoonExcludesProgrammeBeyondTwoHourLookahead() {
        let farFuture = programme(channelId: "bbc1", title: "Far Future Show", startHourOffset: 3, stopHourOffset: 4)
        setStores(["bbc1": [farFuture]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.startingSoon.isEmpty)
    }

    func testStartingSoonExcludesChannelWithCurrentlyAiringProgramme() {
        let current = programme(channelId: "bbc1", title: "Now Airing", startHourOffset: -0.5, stopHourOffset: 0.5)
        let next = programme(channelId: "bbc1", title: "Next Up", startHourOffset: 1, stopHourOffset: 2)
        setStores(["bbc1": [current, next]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.startingSoon.isEmpty)
    }

    func testStartingSoonExcludesChannelWithNoUpcomingProgramme() {
        let past = programme(channelId: "bbc1", title: "Already Ended", startHourOffset: -3, stopHourOffset: -1)
        setStores(["bbc1": [past]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1")], epgVM: epgVM)

        XCTAssertTrue(viewModel.startingSoon.isEmpty)
    }

    // MARK: Derived counts

    func testLiveCountSumsLiveMatchesAndLiveOnAir() {
        let match = programme(channelId: "bbc1", title: "Arsenal vs Chelsea", startHourOffset: -0.5, stopHourOffset: 0.5)
        let onAir = programme(channelId: "itv1", title: "The News", startHourOffset: -0.5, stopHourOffset: 0.5)
        setStores(["bbc1": [match], "itv1": [onAir]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1"), channel(tvgId: "itv1")], epgVM: epgVM)

        XCTAssertEqual(viewModel.liveCount, 2)
    }

    func testSoonCountReflectsStartingSoonBucketSize() {
        let upcomingA = programme(channelId: "bbc1", title: "Show A", startHourOffset: 1, stopHourOffset: 2)
        let upcomingB = programme(channelId: "itv1", title: "Show B", startHourOffset: 1.5, stopHourOffset: 2.5)
        setStores(["bbc1": [upcomingA], "itv1": [upcomingB]])

        viewModel.recompute(channels: [channel(tvgId: "bbc1"), channel(tvgId: "itv1")], epgVM: epgVM)

        XCTAssertEqual(viewModel.soonCount, 2)
    }

    // MARK: Empty input

    func testEmptyChannelListProducesEmptyBuckets() {
        viewModel.recompute(channels: [], epgVM: epgVM)

        XCTAssertTrue(viewModel.liveMatches.isEmpty)
        XCTAssertTrue(viewModel.liveOnAir.isEmpty)
        XCTAssertTrue(viewModel.startingSoon.isEmpty)
        XCTAssertEqual(viewModel.liveCount, 0)
        XCTAssertEqual(viewModel.soonCount, 0)
    }

    func testChannelWithNoEPGDataFallsIntoNoBucket() {
        // No stores set at all — epgVM.currentProgramme/nextProgramme both nil.
        viewModel.recompute(channels: [channel(tvgId: "unknown")], epgVM: epgVM)

        XCTAssertTrue(viewModel.liveMatches.isEmpty)
        XCTAssertTrue(viewModel.liveOnAir.isEmpty)
        XCTAssertTrue(viewModel.startingSoon.isEmpty)
    }
}
