/// NativeStream/NativeStream/Data/ChannelRepositoryImpl.swift
///
/// Fetches and parses M3U playlists for a set of sources, tags the
/// resulting channels with their source, and deduplicates the merged
/// result. No published/view state and no disk persistence — those live
/// in ChannelLoadingViewModel and SettingsDataStore respectively.
/// Mirrors the Android `ChannelRepositoryImpl`.

import Foundation

/// Outcome of fetching a single source, before merging.
struct SourceFetchResult: Sendable {
    let sourceID: UUID
    let channels: [Channel]
    let discoveredEPGURL: URL?
    let error: AppError?
}

/// Merged outcome of fetching every configured source.
struct ChannelFetchResult: Sendable {
    let channels: [Channel]
    let perSource: [SourceFetchResult]
}

protocol ChannelRepository: Sendable {
    func fetchChannels(from sources: [PlaylistSource]) async -> ChannelFetchResult
}

actor ChannelRepositoryImpl: ChannelRepository {

    private let parser: M3UParser
    private let apiClient: APIClient
    private let localHostNames: Set<String> = ["localhost", "127.0.0.1"]

    init(parser: M3UParser = M3UParser(), apiClient: APIClient = .shared) {
        self.parser = parser
        self.apiClient = apiClient
    }

    func fetchChannels(from sources: [PlaylistSource]) async -> ChannelFetchResult {
        var perSource: [SourceFetchResult] = []

        await withTaskGroup(of: SourceFetchResult.self) { group in
            for source in sources {
                group.addTask { [weak self] in
                    guard let self else {
                        return SourceFetchResult(sourceID: source.id, channels: [], discoveredEPGURL: nil, error: nil)
                    }
                    return await self.fetchSingleSource(source)
                }
            }
            for await result in group {
                perSource.append(result)
            }
        }

        let mergedChannels = deduplicated(perSource.flatMap(\.channels))
        return ChannelFetchResult(channels: mergedChannels, perSource: perSource)
    }

    // MARK: - Single source fetch

    private func fetchSingleSource(_ source: PlaylistSource) async -> SourceFetchResult {
        do {
            let data = try await playlistData(for: source)
            let result = try await parser.parse(data: data)
            logParserWarnings(result.warnings)

            let taggedChannels = result.channels.map { channel in
                taggedChannel(channel, sourceID: source.id)
            }

            return SourceFetchResult(
                sourceID: source.id,
                channels: taggedChannels,
                discoveredEPGURL: result.epgURL,
                error: nil
            )
        } catch {
            let appError = error as? AppError ?? .playlistFetchFailed(url: source.url, underlying: error)
            return SourceFetchResult(sourceID: source.id, channels: [], discoveredEPGURL: nil, error: appError)
        }
    }

    private func playlistData(for source: PlaylistSource) async throws -> Data {
            if isLocalSource(source) {
                return try await apiClient.playlistData()
            }
            return try await URLSession.shared.data(from: source.url).0
        }

    /// Whether this source's URL points at the local NativeStream server
    /// (loopback host), in which case playlist data comes from apiClient
    /// rather than a direct URLSession fetch.
    nonisolated func isLocalSource(_ source: PlaylistSource) -> Bool {
        guard let host = source.url.host else { return false }
        return localHostNames.contains(host)
    }

    nonisolated func taggedChannel(_ channel: Channel, sourceID: UUID) -> Channel {
        Channel(
            tvgId:         channel.tvgId,
            name:          channel.name,
            groupTitle:    channel.groupTitle,
            subGroupTitle: channel.subGroupTitle,
            sourceId:      sourceID.uuidString,
            logoURL:       channel.logoURL,
            streamURL:     channel.streamURL,
            streamHeaders: channel.streamHeaders
        )
    }

    private func logParserWarnings(_ warnings: [M3UParseWarning]) {
        for warning in warnings {
            print("⚠️ [M3U] Line \(warning.line): \(warning.reason)")
        }
    }

    nonisolated func deduplicated(_ channels: [Channel]) -> [Channel] {
        var seenChannelIDs = Set<String>()
        return channels.filter { seenChannelIDs.insert($0.id).inserted }
    }
}
