//
//  EPGStore.swift

import Foundation


// MARK: - EPG Store

struct EPGStore: Sendable {
    private let programmes: [String: [Programme]]

    init(programmes: [String: [Programme]]) {
        self.programmes = programmes
    }

    func currentProgramme(for channelId: String) -> Programme? {
        programmes[channelId]?.first(where: { $0.isNow })
    }

    func nextProgramme(for channelId: String) -> Programme? {
        let now = Date()
        return programmes[channelId]?
            .filter { $0.start > now }
            .sorted { $0.start < $1.start }
            .first
    }

    func schedule(for channelId: String) -> [Programme] {
        (programmes[channelId] ?? []).sorted { $0.start < $1.start }
    }

    /// All programmes across all channels within a date range.
    func schedule(for channelId: String, from: Date, to: Date) -> [Programme] {
        (programmes[channelId] ?? [])
            .filter { $0.start >= from && $0.start < to }
            .sorted { $0.start < $1.start }
    }

    var channelCount: Int { programmes.keys.count }
    var programmeCount: Int { programmes.values.reduce(0) { $0 + $1.count } }

    // MARK: - Diagnostic (FX-002)

    /// Fraction of channels that have at least one matching programme.
    func matchRate(for channels: [Channel]) -> Double {
        guard !channels.isEmpty else { return 0 }
        let matched = channels.filter { programmes[$0.tvgId] != nil }.count
        return Double(matched) / Double(channels.count)
    }

    /// Channel IDs that have programmes in the store.
    var knownChannelIds: Set<String> { Set(programmes.keys) }
}


