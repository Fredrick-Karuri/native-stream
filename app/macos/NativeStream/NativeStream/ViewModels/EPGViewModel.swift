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
    var isAvailable: Bool = true   // false when fetch fails
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
}