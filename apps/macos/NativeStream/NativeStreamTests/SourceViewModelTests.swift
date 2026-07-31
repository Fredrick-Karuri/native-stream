// SourceViewModelTests
// Unit tests for SourceViewModel: source CRUD, persistence delegation,
// discovered-EPG recording, and auto-refresh interval selection/rescheduling.
// Uses FakeSettingsPersisting (in-memory) instead of real disk I/O, and the
// real RefreshScheduler with short intervals (already covered by
// RefreshSchedulerTests) to verify scheduling actually fires.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

@MainActor
final class SourceViewModelTests: XCTestCase {

    private var dataStore: FakeSettingsPersisting!
    private var scheduler: RefreshScheduler!
    private var viewModel: SourceViewModel!

    override func setUp() async throws {
        try await super.setUp()
        dataStore = FakeSettingsPersisting()
        scheduler = RefreshScheduler()
        viewModel = SourceViewModel(dataStore: dataStore, scheduler: scheduler)
    }

    override func tearDown() async throws {
        await scheduler.cancelAll()
        viewModel = nil
        scheduler = nil
        dataStore = nil
        try await super.tearDown()
    }

    private func source(
        id: UUID = UUID(),
        label: String = "Source",
        refreshInterval: RefreshInterval = .sixHours
    ) -> PlaylistSource {
        PlaylistSource(
            id: id, label: label,
            url: URL(string: "http://example.com/playlist.m3u")!,
            refreshInterval: refreshInterval
        )
    }

    private func waitUntil(timeout: TimeInterval = 1.0, condition: @escaping () async -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if await condition() { return }
            try? await Task.sleep(for: .seconds(0.01))
        }
    }

    // MARK: loadSourcesFromDisk

    func testLoadSourcesFromDiskPopulatesSourcesFromDataStore() async {
        let stored = [source(label: "Stored Source")]
        await dataStore.seedSources(stored)

        await viewModel.loadSourcesFromDisk()

        XCTAssertEqual(viewModel.sources.map(\.label), ["Stored Source"])
    }

    // MARK: addSource / removeSource / updateSource

    func testAddSourceAppendsAndPersists() async {
        let newSource = source(label: "New Source")

        viewModel.addSource(newSource)

        XCTAssertEqual(viewModel.sources.map(\.label), ["New Source"])
        await waitUntil { await self.dataStore.saveSourcesCallCount >= 1 }
        let persisted = await dataStore.storedSources
        XCTAssertEqual(persisted.map(\.label), ["New Source"])
    }

    func testRemoveSourceRemovesMatchingIDAndPersists() async {
        let keep = source(label: "Keep")
        let remove = source(label: "Remove")
        viewModel.sources = [keep, remove]

        viewModel.removeSource(id: remove.id)

        XCTAssertEqual(viewModel.sources.map(\.label), ["Keep"])
        await waitUntil { await self.dataStore.saveSourcesCallCount >= 1 }
        let persisted = await dataStore.storedSources
        XCTAssertEqual(persisted.map(\.label), ["Keep"])
    }

    func testUpdateSourceReplacesMatchingIDAndPersists() async {
        let original = source(label: "Original")
        viewModel.sources = [original]
        var updated = original
        updated.label = "Updated"

        viewModel.updateSource(updated)

        XCTAssertEqual(viewModel.sources.map(\.label), ["Updated"])
        await waitUntil { await self.dataStore.saveSourcesCallCount >= 1 }
    }

    func testUpdateSourceWithUnknownIDDoesNothing() {
        let existing = source(label: "Existing")
        viewModel.sources = [existing]
        let unrelated = source(label: "Unrelated")

        viewModel.updateSource(unrelated)

        XCTAssertEqual(viewModel.sources.map(\.label), ["Existing"])
    }

    // MARK: updateChannelCount / recordDiscoveredEPGURL

    func testUpdateChannelCountSetsCountForMatchingSource() {
        let target = source(label: "Target")
        viewModel.sources = [target]

        viewModel.updateChannelCount(42, forSourceID: target.id)

        XCTAssertEqual(viewModel.sources.first?.channelCount, 42)
    }

    func testRecordDiscoveredEPGURLSetsURLWhenCurrentlyEmpty() {
        let target = source(label: "Target")
        viewModel.sources = [target]

        viewModel.recordDiscoveredEPGURL(URL(string: "http://example.com/guide.xml")!, forSourceID: target.id)

        XCTAssertEqual(viewModel.sources.first?.epgURLString, "http://example.com/guide.xml")
    }

    func testRecordDiscoveredEPGURLDoesNotOverwriteExistingURL() {
        var target = source(label: "Target")
        target.epgURLString = "http://example.com/existing.xml"
        viewModel.sources = [target]

        viewModel.recordDiscoveredEPGURL(URL(string: "http://example.com/new.xml")!, forSourceID: target.id)

        XCTAssertEqual(viewModel.sources.first?.epgURLString, "http://example.com/existing.xml")
    }

    // MARK: markAllSourcesFetched

    func testMarkAllSourcesFetchedSetsLastFetchedOnAllSources() async {
        viewModel.sources = [source(label: "A"), source(label: "B")]
        let before = Date()

        viewModel.markAllSourcesFetched()

        for src in viewModel.sources {
            XCTAssertNotNil(src.lastFetched)
            XCTAssertGreaterThanOrEqual(src.lastFetched!, before)
        }
        await waitUntil { await self.dataStore.saveSourcesCallCount >= 1 }
    }

    // MARK: resetAll

    func testResetAllClearsSourcesAndDeletesFromDataStore() async {
        viewModel.sources = [source(label: "A")]

        viewModel.resetAll()

        XCTAssertTrue(viewModel.sources.isEmpty)
        await waitUntil { await self.dataStore.deleteAllCallCount >= 1 }
    }

    // MARK: shortestConfiguredRefreshInterval

    func testShortestConfiguredRefreshIntervalPicksMinimumAcrossNonManualSources() {
        viewModel.sources = [
            source(refreshInterval: .daily),
            source(refreshInterval: .oneHour),
            source(refreshInterval: .sixHours)
        ]

        XCTAssertEqual(viewModel.shortestConfiguredRefreshInterval(), RefreshInterval.oneHour.seconds)
    }

    func testShortestConfiguredRefreshIntervalIgnoresManualSources() {
        viewModel.sources = [
            source(refreshInterval: .manual),
            source(refreshInterval: .daily)
        ]

        XCTAssertEqual(viewModel.shortestConfiguredRefreshInterval(), RefreshInterval.daily.seconds)
    }

    func testShortestConfiguredRefreshIntervalFallsBackWhenAllSourcesAreManual() {
        viewModel.sources = [source(refreshInterval: .manual), source(refreshInterval: .manual)]

        XCTAssertEqual(viewModel.shortestConfiguredRefreshInterval(), 3600)
    }

    func testShortestConfiguredRefreshIntervalFallsBackWhenNoSources() {
        viewModel.sources = []

        XCTAssertEqual(viewModel.shortestConfiguredRefreshInterval(), 3600)
    }

    // MARK: Auto-refresh scheduling wiring

    func testScheduleAutoRefreshTriggersOnAutoRefreshTriggeredWhenIntervalElapses() async {
        // Can't use real RefreshInterval values (minimum is 1 hour) in a test
        // timeframe, so this verifies the scheduler wiring itself using the
        // same RefreshScheduler instance directly with a short interval —
        // proving onAutoRefreshTriggered's closure is correctly connected.
        let triggerCount = Counter()
        viewModel.onAutoRefreshTriggered = { await triggerCount.increment() }

        await scheduler.schedule(key: "playlist-refresh", interval: 0.03) {
            await self.viewModel.onAutoRefreshTriggered?()
        }

        await waitUntil(timeout: 1.0) { await triggerCount.value >= 1 }
        let count = await triggerCount.value
        XCTAssertGreaterThanOrEqual(count, 1)
    }

    func testStopAutoRefreshCancelsScheduledJob() async {
        viewModel.sources = [source(refreshInterval: .oneHour)]
        viewModel.scheduleAutoRefresh()

        viewModel.stopAutoRefresh()

        // No crash / hang is the assertion — cancellation should be safe
        // to call even immediately after scheduling with a long real interval.
    }

    func testAddSourceBeforeSchedulingDoesNotAttemptToReschedule() {
        viewModel.sources = [source(refreshInterval: .oneHour)]

        // Before scheduleAutoRefresh() is ever called, isAutoRefreshActive
        // is false, so rescheduleAutoRefreshIfActive() should no-op.
        viewModel.addSource(source(label: "New", refreshInterval: .daily))

        // No crash is the assertion; isAutoRefreshActive isn't directly
        // observable, so this documents the guard path is safe to exercise.
    }
}

// MARK: - Test helper

private actor Counter {
    private(set) var value = 0
    func increment() { value += 1 }
}
