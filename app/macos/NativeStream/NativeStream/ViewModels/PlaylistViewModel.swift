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

    var groups: [String: [Channel]] {
        Dictionary(grouping: channels, by: \.groupTitle)
    }

    var sortedGroupNames: [String] {
        groups.keys.sorted()
    }

    // MARK: - Dependencies

    private let parser = M3UParser()
    private var refreshTask: Task<Void, Never>?

    // MARK: - Init

    init() {
        loadSourcesFromDisk()
    }

    // MARK: - Load

    /// Fetch channels from all configured sources.
    func loadAll() async {
        guard !isLoading else { return }
        isLoading = true
        error = nil

        var allChannels: [Channel] = []

        await withTaskGroup(of: [Channel].self) { group in
            for source in sources {
                group.addTask { [weak self] in
                    guard let self else { return [] }
                    do {
                        let result = try await self.parser.parse(url: source.url)
                        // Log warnings but don't surface to user
                        for warning in result.warnings {
                            print("⚠️ [M3U] Line \(warning.line): \(warning.reason)")
                        }
                        return result.channels
                    } catch {
                        await MainActor.run {
                            self.error = error as? AppError ?? .playlistFetchFailed(url: source.url, underlying: error)
                        }
                        return []
                    }
                }
            }

            for await fetched in group {
                allChannels.append(contentsOf: fetched)
            }
        }

        channels = allChannels
        isLoading = false

        // Mark sources as fetched
        let now = Date()
        sources = sources.map { source in
            var updated = source
            updated.lastFetched = now
            return updated
        }
        saveSourcesToDisk()
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

    private func loadSourcesFromDisk() {
        guard let data = try? Data(contentsOf: sourcesURL),
              let loaded = try? JSONDecoder().decode([PlaylistSource].self, from: data) else {
            return
        }
        sources = loaded
    }
}