// NS-011: Channel
// Core data model representing a single TV channel in the playlist.

import Foundation

struct Channel: Identifiable, Codable, Sendable, Hashable {
    let id: UUID
    let tvgId: String
    let name: String
    let groupTitle: String
    let logoURL: URL?
    let streamURL: URL

    init(
        id: UUID = UUID(),
        tvgId: String,
        name: String,
        groupTitle: String = "Uncategorised",
        logoURL: URL? = nil,
        streamURL: URL
    ) {
        self.id = id
        self.tvgId = tvgId
        self.name = name
        self.groupTitle = groupTitle
        self.logoURL = logoURL
        self.streamURL = streamURL
    }

    // MARK: - Hashable
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: Channel, rhs: Channel) -> Bool {
        lhs.id == rhs.id
    }
}