// NS-031 + NS-032: PlaylistViewModel
// Owns the channel list, loading state, and auto-refresh scheduling.

import Foundation
import Observation

@Observable
@MainActor
final class PlaylistViewModel {

    // MARK: - Published state

    var channels: [Channel] = []
    var sources: [PlaylistSource] = []
    var isLoading: Bool = false
    var error: AppError? = nil

    // MARK: - Computed
    private(set) var groups: [String: [Channel]] = [:]
    private(set) var sortedGroupNames: [String] = []

    // MARK: - Dependencies

    private let parser = M3UParser()
    private var refreshTask: Task<Void, Never>?
    private let settings: SettingsStore

    // MARK: - Init

    init(settings: SettingsStore) {
        self.settings = settings
        loadSourcesFromDisk()
        let cached = loadChannelsFromDisk()
        if !cached.isEmpty {
            channels = cached
            groups = Dictionary(grouping: cached, by: \.groupTitle)
            sortedGroupNames = groups.keys.sorted()
        }
    }

    // MARK: - Load

    /// Fetch channels from all configured sources.
    func loadAll() async {
        guard !isLoading else { return }

        // Serve stale cache immediately if available
        let hasCache = !channels.isEmpty
        if !hasCache {
            isLoading = true
        }
        error = nil

        var allChannels: [Channel] = []

        await withTaskGroup(of: (channels: [Channel], epgURL: URL?, sourceID: UUID).self) { group in
            for source in sources {
                group.addTask { [weak self] in
                    guard let self else { return ([], nil, source.id) }
                    do {
                        let data: Data
                        if source.url.host == "localhost" || source.url.host == "127.0.0.1" {
                            data = try await APIClient.shared.playlistData()
                        } else {
                            data = try await URLSession.shared.data(from: source.url).0
                        }
                        let result = try await self.parser.parse(data: data)
                        for warning in result.warnings {
                            print("⚠️ [M3U] Line \(warning.line): \(warning.reason)")
                        }
                        return (result.channels, result.epgURL, source.id)
                    } catch {
                        await MainActor.run {
                            self.error = error as? AppError ?? .playlistFetchFailed(url: source.url, underlying: error)
                        }
                        return ([], nil, source.id)
                    }
                }
            }
            for await fetched in group {
                allChannels.append(contentsOf: fetched.channels)
                if let epgURL = fetched.epgURL,
                   let idx = sources.firstIndex(where: { $0.id == fetched.sourceID }),
                   sources[idx].epgURLString.isEmpty {
                    sources[idx].epgURLString = epgURL.absoluteString
                    if settings.epgURLString.isEmpty {
                        settings.epgURLString = epgURL.absoluteString
                    }
                }
            }
        }

        channels = allChannels
        groups = Dictionary(grouping: channels, by: \.groupTitle)
        sortedGroupNames = groups.keys.sorted()
        isLoading = false
        let now = Date()
        sources = sources.map { var s = $0; s.lastFetched = now; return s }
        saveSourcesToDisk()
        saveChannelsToDisk(channels)
    }
    
    // add after loadAll()
    func insert(_ channel: Channel) {
        channels.append(channel)
        groups = Dictionary(grouping: channels, by: \.groupTitle)
        sortedGroupNames = groups.keys.sorted()
    }
 
    /// Refresh a single source and merge changes.
    func refresh(source: PlaylistSource) async {
        do {
            let result = try await parser.parse(url: source.url)
            // Diff: replace all channels from this source
            // For simplicity in V1, we reload all sources
            await loadAll()
        } catch {
            self.error = error as? AppError ?? .playlistFetchFailed(url: source.url, underlying: error)
        }
    }

    // MARK: - Auto-refresh scheduling (NS-032)

    func scheduleAutoRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }

                // Find shortest non-manual interval
                let shortestInterval = await MainActor.run {
                    self.sources
                        .filter { $0.refreshInterval != .manual }
                        .map { $0.refreshInterval.seconds }
                        .min() ?? 3600
                }

                guard shortestInterval > 0 else {
                    try? await Task.sleep(for: .seconds(3600))
                    continue
                }

                try? await Task.sleep(for: .seconds(shortestInterval))

                guard !Task.isCancelled else { return }

                await MainActor.run {
                    Task { await self.loadAll() }
                }
            }
        }
    }

    func stopAutoRefresh() {
        refreshTask?.cancel()
        refreshTask = nil
    }

    // MARK: - Source management

    func addSource(_ source: PlaylistSource) {
        sources.append(source)
        saveSourcesToDisk()
    }

    func removeSource(id: UUID) {
        sources.removeAll { $0.id == id }
        saveSourcesToDisk()
    }

    func updateSource(_ source: PlaylistSource) {
        if let index = sources.firstIndex(where: { $0.id == source.id }) {
            sources[index] = source
            saveSourcesToDisk()
        }
    }

    // MARK: - Persistence

    private var sourcesURL: URL {
        FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("NativeStream/playlist_sources.json")
    }

    private func saveSourcesToDisk() {
        do {
            try FileManager.default.createDirectory(
                at: sourcesURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            let data = try JSONEncoder().encode(sources)
            try data.write(to: sourcesURL, options: .atomic)
        } catch {
            print("⚠️ Failed to save playlist sources: \(error)")
        }
    }
    
    
    private var channelCacheURL: URL {
        FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("NativeStream/channel_cache.json")
    }

    private func saveChannelsToDisk(_ channels: [Channel]) {
        do {
            try FileManager.default.createDirectory(
                at: channelCacheURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            let data = try JSONEncoder().encode(channels)
            try data.write(to: channelCacheURL, options: .atomic)
        } catch {
            print("⚠️ Failed to save channel cache: \(error)")
        }
    }

    private func loadChannelsFromDisk() -> [Channel] {
        guard let data = try? Data(contentsOf: channelCacheURL),
              let loaded = try? JSONDecoder().decode([Channel].self, from: data)
        else { return [] }
        return loaded
    }

    private func loadSourcesFromDisk() {
        guard let data = try? Data(contentsOf: sourcesURL),
              let loaded = try? JSONDecoder().decode([PlaylistSource].self, from: data) else {
            return
        }
        sources = loaded
    }
}
