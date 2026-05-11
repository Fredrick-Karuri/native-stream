// EPGViewModel
// Owns EPG data loading and exposes current/next programme lookups.

import Foundation
import Observation

@Observable
@MainActor
final class EPGViewModel {

    // MARK: - State

    var store: EPGStore? = nil
    var isLoading: Bool = false
    var isAvailable: Bool = true
    var epgURL: URL? = nil

    private let parser = EPGParser()

    // MARK: - Load

    func load() async {
        guard let url = epgURL else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            let loaded = try await parser.parse(url: url)
            store = loaded
            isAvailable = true
        } catch {
            isAvailable = false
            print("⚠️ [EPG] Failed to load: \(error.localizedDescription)")
        }
    }

    // MARK: - Queries

    func currentProgramme(for channel: Channel) -> Programme? {
        store?.currentProgramme(for: channel.tvgId)
    }

    func nextProgramme(for channel: Channel) -> Programme? {
        store?.nextProgramme(for: channel.tvgId)
    }

    func schedule(for channel: Channel, hours: Int = 6) -> [Programme] {
        guard let all = store?.schedule(for: channel.tvgId) else { return [] }
        let cutoff = Date().addingTimeInterval(TimeInterval(hours * 3600))
        return all.filter { $0.stop > Date() && $0.start < cutoff }
    }

    // MARK: - Sport helpers (UX-008)

    /// True when at least one channel has a current or upcoming programme matching this sport.
    func hasContent(for sport: SportCategory, in channels: [Channel]) -> Bool {
        liveCount(for: sport, in: channels) > 0 || upcomingCount(for: sport, in: channels) > 0
    }

    /// Number of channels with a currently live programme matching this sport.
    func liveCount(for sport: SportCategory, in channels: [Channel]) -> Int {
        channels.filter { channel in
            guard let prog = currentProgramme(for: channel) else { return false }
            return sport.epgKeywords.contains { prog.title.lowercased().contains($0) }
        }.count
    }

    /// Number of channels with an upcoming programme matching this sport.
    func upcomingCount(for sport: SportCategory, in channels: [Channel]) -> Int {
        channels.filter { channel in
            guard let prog = nextProgramme(for: channel) else { return false }
            return sport.epgKeywords.contains { prog.title.lowercased().contains($0) }
        }.count
    }

    /// Sports that have any content, sorted by live count descending.
    func activeSports(in channels: [Channel]) -> [SportCategory] {
        SportCategory.allCases
            .filter { hasContent(for: $0, in: channels) }
            .sorted { liveCount(for: $0, in: channels) > liveCount(for: $1, in: channels) }
    }
}
