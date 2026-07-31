/// NativeStream/NativeStream/ViewModels/SourceViewModel.swift
///
/// Owns the list of configured playlist sources: CRUD and disk persistence
/// (delegated to SettingsDataStore). Auto-refresh timing is delegated to
/// the shared RefreshScheduler service rather than reimplemented here —
/// this type only decides *what* interval to schedule and notifies
/// `onAutoRefreshTriggered` when it fires. Mirrors the Android `SourceViewModel`.

import Foundation
import Observation

@Observable
@MainActor
final class SourceViewModel {

    private static let fallbackRefreshIntervalSeconds: TimeInterval = 3600
    private static let refreshSchedulerKey = "playlist-refresh"

    // MARK: - Published state

    var sources: [PlaylistSource] = []

    // MARK: - Hooks

    /// Set by the composition root to trigger a channel reload when the
    /// shortest configured refresh interval elapses.
    var onAutoRefreshTriggered: (() async -> Void)?

    // MARK: - Dependencies

    private let dataStore: SettingsPersisting
    private let scheduler: RefreshScheduler
    private var isAutoRefreshActive = false

    // MARK: - Init

    init(dataStore: SettingsPersisting = SettingsDataStore(), scheduler: RefreshScheduler = RefreshScheduler()) {
        self.dataStore = dataStore
        self.scheduler = scheduler
    }

    func loadSourcesFromDisk() async {
        sources = await dataStore.loadSources()
    }

    // MARK: - Source CRUD

    func addSource(_ source: PlaylistSource) {
        sources.append(source)
        persistSources()
        rescheduleAutoRefreshIfActive()
    }

    func removeSource(id: UUID) {
        sources.removeAll { $0.id == id }
        persistSources()
        rescheduleAutoRefreshIfActive()
    }

    func updateSource(_ source: PlaylistSource) {
        guard let index = sources.firstIndex(where: { $0.id == source.id }) else { return }
        sources[index] = source
        persistSources()
        rescheduleAutoRefreshIfActive()
    }

    func updateChannelCount(_ count: Int, forSourceID sourceID: UUID) {
        guard let index = sources.firstIndex(where: { $0.id == sourceID }) else { return }
        sources[index].channelCount = count
    }

    func recordDiscoveredEPGURL(_ epgURL: URL, forSourceID sourceID: UUID) {
        guard let index = sources.firstIndex(where: { $0.id == sourceID }),
              sources[index].epgURLString.isEmpty
        else { return }
        sources[index].epgURLString = epgURL.absoluteString
    }

    func markAllSourcesFetched(at date: Date = Date()) {
        sources = sources.map { var source = $0; source.lastFetched = date; return source }
        persistSources()
    }

    func resetAll() {
        stopAutoRefresh()
        sources = []
        Task { await dataStore.deleteAll() }
    }

    // MARK: - Auto-refresh scheduling

    /// Starts (or re-tunes) the recurring auto-refresh job at the shortest
    /// non-manual interval across all sources. Re-invoked on every source
    /// mutation while active, so a newly added shorter interval takes
    /// effect on the next cycle rather than waiting for the old one to
    /// finish
    func scheduleAutoRefresh() {
        isAutoRefreshActive = true
        let interval = shortestConfiguredRefreshInterval()
        guard interval > 0 else {
            Task { await scheduler.cancel(key: Self.refreshSchedulerKey) }
            return
        }
        Task {
            await scheduler.schedule(key: Self.refreshSchedulerKey, interval: interval) { [weak self] in
                await self?.onAutoRefreshTriggered?()
            }
        }
    }

    func stopAutoRefresh() {
        isAutoRefreshActive = false
        Task { await scheduler.cancel(key: Self.refreshSchedulerKey) }
    }

    // MARK: - Persistence

    private func persistSources() {
        Task { await dataStore.saveSources(sources) }
    }

    private func rescheduleAutoRefreshIfActive() {
        guard isAutoRefreshActive else { return }
        scheduleAutoRefresh()
    }

    func shortestConfiguredRefreshInterval() -> TimeInterval {
        sources
            .filter { $0.refreshInterval != .manual }
            .map { $0.refreshInterval.seconds }
            .min() ?? Self.fallbackRefreshIntervalSeconds
    }
}
