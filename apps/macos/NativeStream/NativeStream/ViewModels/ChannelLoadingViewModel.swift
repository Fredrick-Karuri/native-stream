/// NativeStream/NativeStream/ViewModels/ChannelLoadingViewModel.swift
///
/// Owns the loaded channel list, loading/error state, and grouping.
/// Delegates fetching/parsing to a ChannelRepository, reads which sources
/// to load from SourceViewModel, and delegates channel-cache persistence
/// to SettingsDataStore. Mirrors the Android `ChannelLoadingViewModel`.

import Foundation
import Observation

@Observable
@MainActor
final class ChannelLoadingViewModel {

    // MARK: - Published state

    var channels: [Channel] = []
    var isLoading: Bool = false
    var error: AppError?

    // MARK: - Computed

    private(set) var groups: [String: [Channel]] = [:]
    private(set) var sortedGroupNames: [String] = []

    // MARK: - Dependencies

    private let repository: ChannelRepository
    private let dataStore: SettingsPersisting
    private let sourceViewModel: SourceViewModel
    private let settings: SettingsStore

    // MARK: - Init

    init(
        sourceViewModel: SourceViewModel,
        settings: SettingsStore,
        repository: ChannelRepository = ChannelRepositoryImpl(),
        dataStore: SettingsPersisting = SettingsDataStore()
    ) {
        self.sourceViewModel = sourceViewModel
        self.settings = settings
        self.repository = repository
        self.dataStore = dataStore
    }

    func loadCachedChannelsFromDisk() async {
        let cached = await dataStore.loadChannels()
        guard !cached.isEmpty else { return }
        applyChannels(cached)
    }

    // MARK: - Load

    /// Fetch channels from every source currently configured on SourceViewModel.
    func loadAll() async {
        guard !isLoading else { return }

        let hasCache = !channels.isEmpty
        if !hasCache {
            isLoading = true
        }
        error = nil

        let result = await repository.fetchChannels(from: sourceViewModel.sources)
        applyFetchResult(result)

        isLoading = false
        sourceViewModel.markAllSourcesFetched()
        await dataStore.saveChannels(channels)
    }

    /// Refresh a single source. In V1 this reloads every source, since
    /// channels aren't tracked per-source in the merged list.
    func refresh(source: PlaylistSource) async {
        await loadAll()
    }

    func insert(_ channel: Channel) {
        applyChannels(channels + [channel])
    }

    func resetAll() {
        applyChannels([])
        error = nil
    }

    // MARK: - Result application

    private func applyFetchResult(_ result: ChannelFetchResult) {
        for sourceResult in result.perSource {
            if let discoveredEPGURL = sourceResult.discoveredEPGURL {
                sourceViewModel.recordDiscoveredEPGURL(discoveredEPGURL, forSourceID: sourceResult.sourceID)
                if settings.epgURLString.isEmpty {
                    settings.epgURLString = discoveredEPGURL.absoluteString
                }
            }
            sourceViewModel.updateChannelCount(sourceResult.channels.count, forSourceID: sourceResult.sourceID)
            if let sourceError = sourceResult.error {
                error = sourceError
            }
        }
        applyChannels(result.channels)
    }

    private func applyChannels(_ newChannels: [Channel]) {
        channels = newChannels
        groups = Dictionary(grouping: channels, by: \.groupTitle)
        sortedGroupNames = groups.keys.sorted()
    }
}
