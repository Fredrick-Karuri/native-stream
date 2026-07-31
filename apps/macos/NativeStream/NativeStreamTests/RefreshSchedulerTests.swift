// RefreshSchedulerTests
// Unit tests for RefreshScheduler: scheduling, cancel-and-replace, the
// interval<=0 guard, per-key cancellation, and cancelAll. Uses short real
// intervals (tens of milliseconds) rather than mocking Task.sleep, so tests
// wait briefly on real timing but stay fast and deterministic in practice.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class RefreshSchedulerTests: XCTestCase {

    private var scheduler: RefreshScheduler!

    override func setUp() async throws {
        try await super.setUp()
        scheduler = RefreshScheduler()
    }

    override func tearDown() async throws {
        await scheduler.cancelAll()
        scheduler = nil
        try await super.tearDown()
    }

    // MARK: Basic firing

    func testScheduledJobFiresAfterInterval() async {
        let fireCount = Counter()

        await scheduler.schedule(key: "test", interval: 0.05) {
            await fireCount.increment()
        }

        await waitUntil(timeout: 1.0) { await fireCount.value >= 1 }

        let count = await fireCount.value
        XCTAssertGreaterThanOrEqual(count, 1)
    }

    func testScheduledJobFiresRepeatedly() async {
        let fireCount = Counter()

        await scheduler.schedule(key: "test", interval: 0.03) {
            await fireCount.increment()
        }

        await waitUntil(timeout: 1.0) { await fireCount.value >= 3 }

        let count = await fireCount.value
        XCTAssertGreaterThanOrEqual(count, 3)
    }

    // MARK: interval guard

    func testNonPositiveIntervalDoesNotScheduleAnything() async {
        let fireCount = Counter()

        await scheduler.schedule(key: "test", interval: 0) {
            await fireCount.increment()
        }

        try? await Task.sleep(for: .seconds(0.2))

        let count = await fireCount.value
        XCTAssertEqual(count, 0)
    }

    func testNegativeIntervalDoesNotScheduleAnything() async {
        let fireCount = Counter()

        await scheduler.schedule(key: "test", interval: -1) {
            await fireCount.increment()
        }

        try? await Task.sleep(for: .seconds(0.2))

        let count = await fireCount.value
        XCTAssertEqual(count, 0)
    }

    // MARK: Replace-on-same-key

    func testSchedulingWithSameKeyCancelsPreviousJob() async {
        let firstJobCount = Counter()
        let secondJobCount = Counter()

        await scheduler.schedule(key: "test", interval: 0.03) {
            await firstJobCount.increment()
        }
        try? await Task.sleep(for: .seconds(0.05))

        await scheduler.schedule(key: "test", interval: 0.03) {
            await secondJobCount.increment()
        }

        await waitUntil(timeout: 1.0) { await secondJobCount.value >= 2 }

        let firstCountAtReplaceTime = await firstJobCount.value
        try? await Task.sleep(for: .seconds(0.1))
        let firstCountAfterWaiting = await firstJobCount.value

        XCTAssertEqual(
            firstCountAtReplaceTime, firstCountAfterWaiting,
            "First job should not fire again after being replaced"
        )
    }

    // MARK: cancel(key:)

    func testCancelStopsFutureFiringsForThatKey() async {
        let fireCount = Counter()

        await scheduler.schedule(key: "test", interval: 0.03) {
            await fireCount.increment()
        }
        await waitUntil(timeout: 1.0) { await fireCount.value >= 1 }

        await scheduler.cancel(key: "test")
        let countAtCancel = await fireCount.value
        try? await Task.sleep(for: .seconds(0.15))
        let countAfterWaiting = await fireCount.value

        XCTAssertEqual(countAtCancel, countAfterWaiting)
    }

    func testCancelForUnknownKeyDoesNothingHarmful() async {
        await scheduler.cancel(key: "never-scheduled")
        // No crash, no error — nothing to assert beyond reaching this line.
    }

    func testCancelOnlyAffectsTheGivenKey() async {
        let keptJobCount = Counter()

        await scheduler.schedule(key: "keep", interval: 0.03) {
            await keptJobCount.increment()
        }
        await scheduler.schedule(key: "remove", interval: 0.03) {}

        await scheduler.cancel(key: "remove")
        await waitUntil(timeout: 1.0) { await keptJobCount.value >= 2 }

        let count = await keptJobCount.value
        XCTAssertGreaterThanOrEqual(count, 2)
    }

    // MARK: cancelAll

    func testCancelAllStopsAllScheduledJobs() async {
        let firstCount = Counter()
        let secondCount = Counter()

        await scheduler.schedule(key: "a", interval: 0.03) { await firstCount.increment() }
        await scheduler.schedule(key: "b", interval: 0.03) { await secondCount.increment() }
        await waitUntil(timeout: 1.0) {
            let firstValue = await firstCount.value
            let secondValue = await secondCount.value
            return firstValue >= 1 && secondValue >= 1
        }

        await scheduler.cancelAll()
        let firstAtCancel = await firstCount.value
        let secondAtCancel = await secondCount.value
        try? await Task.sleep(for: .seconds(0.15))

        let firstAfterWaiting = await firstCount.value
        let secondAfterWaiting = await secondCount.value

        XCTAssertEqual(firstAtCancel, firstAfterWaiting)
        XCTAssertEqual(secondAtCancel, secondAfterWaiting)
    }
}

// MARK: - Test helpers

/// Thread-safe counter for observing how many times an async job fired.
private actor Counter {
    private(set) var value = 0
    func increment() { value += 1 }
}

/// Polls `condition` until it returns true or `timeout` elapses.
private func waitUntil(
    timeout: TimeInterval,
    pollInterval: TimeInterval = 0.01,
    condition: @escaping () async -> Bool
) async {
    let deadline = Date().addingTimeInterval(timeout)
    while Date() < deadline {
        if await condition() { return }
        try? await Task.sleep(for: .seconds(pollInterval))
    }
}
