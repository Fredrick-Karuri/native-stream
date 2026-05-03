// NS-013: PlaylistSource + RefreshInterval
// Represents a configured M3U playlist source with its refresh schedule.

import Foundation


struct PlaylistSource: Identifiable, Codable, Sendable {
    let id: UUID
    var label: String
    var url: URL
    var refreshInterval: RefreshInterval
    var lastFetched: Date?

    init(
        id: UUID = UUID(),
        label: String,
        url: URL,
        refreshInterval: RefreshInterval = .sixHours,
        lastFetched: Date? = nil
    ) {
        self.id = id
        self.label = label
        self.url = url
        self.refreshInterval = refreshInterval
        self.lastFetched = lastFetched
    }

    /// Whether this source is due for a refresh.
    var needsRefresh: Bool {
        guard refreshInterval != .manual else { return false }
        guard let last = lastFetched else { return true }
        return Date().timeIntervalSince(last) >= refreshInterval.seconds
    }
}