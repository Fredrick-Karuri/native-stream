// Channel
// Core data model representing a single TV channel in the playlist.

import Foundation

struct Channel: Identifiable, Codable, Sendable, Hashable {
    let id: String
    let tvgId: String
    let name: String
    let groupTitle: String
    let subGroupTitle: String        // MAC-BROWSE-007: sub-group drill
    let sourceId: String             // MAC-BROWSE-002: per-source filtering
    let logoURL: URL?
    let streamURL: URL
    let streamHeaders: [String: String]

    init(
        tvgId: String,
        name: String,
        groupTitle: String           = "Uncategorised",
        subGroupTitle: String        = "",
        sourceId: String             = "",
        logoURL: URL?                = nil,
        streamURL: URL,
        streamHeaders: [String: String] = [:]
    ) {
        self.id = if !tvgId.isEmpty {
            tvgId
        } else {
            streamURL.absoluteString
        }
        self.tvgId         = tvgId
        self.name          = name
        self.groupTitle    = groupTitle
        self.subGroupTitle = subGroupTitle
        self.sourceId      = sourceId
        self.logoURL       = logoURL
        self.streamURL     = streamURL
        self.streamHeaders = streamHeaders
    }

    // ── Codable with defaults for backward compatibility ──────────────────────
    enum CodingKeys: String, CodingKey {
        case id, tvgId, name, groupTitle, subGroupTitle
        case sourceId, logoURL, streamURL, streamHeaders
    }

    init(from decoder: Decoder) throws {
        let c          = try decoder.container(keyedBy: CodingKeys.self)
        id             = try c.decode(String.self,              forKey: .id)
        tvgId          = try c.decode(String.self,              forKey: .tvgId)
        name           = try c.decode(String.self,              forKey: .name)
        groupTitle     = try c.decode(String.self,              forKey: .groupTitle)
        subGroupTitle  = try c.decodeIfPresent(String.self,     forKey: .subGroupTitle)  ?? ""
        sourceId       = try c.decodeIfPresent(String.self,     forKey: .sourceId)       ?? ""
        logoURL        = try c.decodeIfPresent(URL.self,        forKey: .logoURL)
        streamURL      = try c.decode(URL.self,                 forKey: .streamURL)
        streamHeaders  = try c.decodeIfPresent([String: String].self, forKey: .streamHeaders) ?? [:]
    }

    // ── Hashable ──────────────────────────────────────────────────────────────
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
    static func == (lhs: Channel, rhs: Channel) -> Bool { lhs.id == rhs.id }
}
