// PlaylistSource + RefreshInterval
// Represents a configured M3U playlist source with its refresh schedule.

import Foundation
import SwiftUI

struct PlaylistSource: Identifiable, Codable, Sendable, Equatable {
    let id: UUID
    var label: String
    var url: URL
    var colorHex: String
    var channelCount: Int
    var refreshInterval: RefreshInterval
    var lastFetched: Date?
    var epgURLString: String

    var epgURL: URL? { URL(string: epgURLString) }

    // ── Sentinel — all sources merged view ───────────────────────────────────
    static let allSources = PlaylistSource(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000000")!,
        label: "All Sources",
        url: URL(string: "about:blank")!,
        colorHex: "",
        channelCount: 0,
        refreshInterval: .manual
    )

    var isAll: Bool { id == Self.allSources.id }

    // ── Color constants ───────────────────────────────────────────────────────
    static let colorBlue  = "#0EA5E9"
    static let colorGreen = "#10B981"
    static let colorAmber = "#F59E0B"

    // ── Parsed color for SwiftUI ──────────────────────────────────────────────
    var color: Color {
        guard !colorHex.isEmpty else { return .secondary }
        return Color(hex: colorHex) ?? .secondary
    }

    init(
        id: UUID = UUID(),
        label: String,
        url: URL,
        colorHex: String = PlaylistSource.colorBlue,
        channelCount: Int = 0,
        refreshInterval: RefreshInterval = .sixHours,
        lastFetched: Date? = nil,
        epgURLString: String = ""
    ) {
        self.id              = id
        self.label           = label
        self.url             = url
        self.colorHex        = colorHex
        self.channelCount    = channelCount
        self.refreshInterval = refreshInterval
        self.lastFetched     = lastFetched
        self.epgURLString    = epgURLString
    }

    var needsRefresh: Bool {
        guard refreshInterval != .manual else { return false }
        guard let last = lastFetched else { return true }
        return Date().timeIntervalSince(last) >= refreshInterval.seconds
    }

    // ── Codable with defaults for backward compatibility ──────────────────────
    enum CodingKeys: String, CodingKey {
        case id, label, url, colorHex, channelCount
        case refreshInterval, lastFetched, epgURLString
    }

    init(from decoder: Decoder) throws {
        let container    = try decoder.container(keyedBy: CodingKeys.self)
        id               = try container.decode(UUID.self, forKey: .id)
        label            = try container.decode(String.self, forKey: .label)
        url              = try container.decode(URL.self, forKey: .url)
        colorHex         = try container.decodeIfPresent(
            String.self, forKey: .colorHex) ?? PlaylistSource.colorBlue
        channelCount     = try container.decodeIfPresent(
            Int.self, forKey: .channelCount) ?? 0
        refreshInterval  = try container.decode(
            RefreshInterval.self, forKey: .refreshInterval)
        lastFetched      = try container.decodeIfPresent(
            Date.self, forKey: .lastFetched)
        epgURLString     = try container.decodeIfPresent(
            String.self, forKey: .epgURLString) ?? ""
    }
    static func == (lhs: PlaylistSource, rhs: PlaylistSource) -> Bool {
        lhs.id == rhs.id
    }
}
