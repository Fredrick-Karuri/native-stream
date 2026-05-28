// NS-012: Programme
// Represents a single EPG programme entry linked to a channel.

import Foundation

struct Programme: Codable, Sendable {
    let channelId: String
    let title: String
    let start: Date
    let stop: Date

    /// Elapsed fraction of the programme duration, clamped 0–1.
    var progress: Double {
        let now = Date()
        guard now >= start else { return 0 }
        guard now < stop else { return 1 }
        let duration = stop.timeIntervalSince(start)
        guard duration > 0 else { return 0 }
        return min(1, max(0, now.timeIntervalSince(start) / duration))
    }

    /// Human-readable start time (e.g. "15:00").
    var startTimeString: String {
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        return fmt.string(from: start)
    }

    /// Human-readable stop time (e.g. "16:45").
    var stopTimeString: String {
        let fmt = DateFormatter()
        fmt.dateFormat = "HH:mm"
        return fmt.string(from: stop)
    }

    /// Whether this programme is currently airing.
    var isNow: Bool {
        let now = Date()
        return now >= start && now < stop
    }

    var timeRemainingString: String {
        let remaining = Int(stop.timeIntervalSinceNow / 60)
        return remaining > 0 ? "\(remaining)m left" : "Ending"
    }

    var isSportMatch: Bool {
        let keywords = SportCategory.allCases.flatMap { $0.epgKeywords }
        return keywords.contains { title.lowercased().contains($0) }
    }
}
