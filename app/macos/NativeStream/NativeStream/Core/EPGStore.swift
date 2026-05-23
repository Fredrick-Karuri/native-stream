//
//  EPGStore.swift

import Foundation

// MARK: - EPG Store

struct EPGStore: Sendable {
    private let programmes: [String: [Programme]]
    private let programmesLower: [String: [Programme]]

    init(programmes: [String: [Programme]]) {
        self.programmes = programmes
        self.programmesLower = Dictionary(
            uniqueKeysWithValues: programmes.map { ($0.key.lowercased(), $0.value) }
        )
    }

    // MARK: - Queries

    func currentProgramme(for channelId: String) -> Programme? {
        resolve(channelId)?.first(where: { $0.isNow })
    }

    func nextProgramme(for channelId: String) -> Programme? {
        let now = Date()
        return resolve(channelId)?
            .filter { $0.start > now }
            .sorted { $0.start < $1.start }
            .first
    }

    func schedule(for channelId: String) -> [Programme] {
        (resolve(channelId) ?? []).sorted { $0.start < $1.start }
    }

    /// FX-004: explicit date range query for ScheduleScreen
    func schedule(for channelId: String, from: Date, to: Date) -> [Programme] {
        (resolve(channelId) ?? [])
            .filter { $0.start >= from && $0.start < to }
            .sorted { $0.start < $1.start }
    }

    // MARK: - FX-002: exact match → case-insensitive fallback

    private func resolve(_ channelId: String) -> [Programme]? {
        programmes[channelId] ?? programmesLower[channelId.lowercased()]
    }

    // MARK: - Diagnostics

    func matchRate(for channels: [Channel]) -> Double {
        guard !channels.isEmpty else { return 0 }
        let matched = channels.filter { resolve($0.tvgId) != nil }.count
        return Double(matched) / Double(channels.count)
    }

    var knownChannelIds: Set<String> { Set(programmes.keys) }
    var channelCount: Int { programmes.keys.count }
    var programmeCount: Int { programmes.values.reduce(0) { $0 + $1.count } }
}

