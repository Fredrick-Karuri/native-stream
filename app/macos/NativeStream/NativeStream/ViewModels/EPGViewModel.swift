// EPGViewModel.swift — FX-001, FX-002, FX-004, FX-005
import Foundation
import Observation
import Compression

@Observable
@MainActor
final class EPGViewModel {

    var store: EPGStore? = nil
    var isLoading: Bool = false
    var isAvailable: Bool = true
    var epgURL: URL? = nil           // FX-005: set by AppShell from SettingsStore

    private let parser = EPGParser()

    // MARK: - Load
func load() async {
    isLoading = true
    defer { isLoading = false }

    do {
        let data: Data
        if let directURL = epgURL.map(normalizeEPGURL) {
            if directURL.isFileURL {
                data = try Data(contentsOf: directURL)
            } else {
                let (fetched, response) = try await URLSession.shared.data(from: directURL)
                guard let http = response as? HTTPURLResponse,
                      (200...299).contains(http.statusCode) else {
                    throw AppError.epgFetchFailed(underlying: URLError(.badServerResponse))
                }
                data = fetched
            }
        } else {
            data = try await APIClient.shared.epgData()
        }

        let decompressed: Data
        if let url = epgURL, url.pathExtension == "gz" {
            guard data.count > 18 else { throw AppError.epgParseError(reason: "GZ data too short") }
            let payload = data.subdata(in: 10..<data.count - 8)
            let bufferSize = 10_000_000
            var decompressedData = Data(count: bufferSize)
            let written = decompressedData.withUnsafeMutableBytes { outPtr in
                payload.withUnsafeBytes { inPtr in
                    compression_decode_buffer(
                        outPtr.bindMemory(to: UInt8.self).baseAddress!,
                        bufferSize,
                        inPtr.bindMemory(to: UInt8.self).baseAddress!,
                        payload.count,
                        nil,
                        COMPRESSION_ZLIB
                    )
                }
            }
            guard written > 0 else { throw AppError.epgParseError(reason: "GZ decompression failed") }
            decompressed = decompressedData.prefix(written)
        } else {
            decompressed = data
        }

        let loaded = try await Task.detached(priority: .userInitiated) { [parser] in
            try parser.parse(data: decompressed)
        }.value

        store = loaded
        print("✅ [EPG] Loaded \(loaded.programmeCount) programmes for \(loaded.channelCount) channels")
        isAvailable = true

    } catch {
        isAvailable = false
        print("⚠️ [EPG] Failed to load: \(error.localizedDescription)")
    }
}

private func normalizeEPGURL(_ url: URL) -> URL {
    guard url.host == "github.com" else { return url }
    let fixed = url.absoluteString
        .replacingOccurrences(of: "https://github.com/", with: "https://raw.githubusercontent.com/")
        .replacingOccurrences(of: "/raw/", with: "/")
    return URL(string: fixed) ?? url
}

    // MARK: - Diagnostic (FX-002)

    func logMatchDiagnostic(for channels: [Channel]) {
        guard let store else {
            print("⚠️ [EPG] No store loaded — cannot compute match rate")
            return
        }
        let rate = store.matchRate(for: channels)
        let unmatched = channels.filter { !store.knownChannelIds.contains($0.tvgId) }
        print("📺 [EPG] Match rate: \(Int(rate * 100))% (\(channels.count - unmatched.count)/\(channels.count))")
        if !unmatched.isEmpty {
            print("⚠️ [EPG] Unmatched channels:")
            unmatched.prefix(20).forEach { print("   - \($0.name) | tvgId: '\($0.tvgId)'") }
        }
    }

    // MARK: - Queries

    func currentProgramme(for channel: Channel) -> Programme? {
        store?.currentProgramme(for: channel.tvgId)
    }

    func nextProgramme(for channel: Channel) -> Programme? {
        store?.nextProgramme(for: channel.tvgId)
    }

    /// Programmes for a channel within the next N hours from now.
    func schedule(for channel: Channel, hours: Int = 6) -> [Programme] {
        guard let all = store?.schedule(for: channel.tvgId) else { return [] }
        let cutoff = Date().addingTimeInterval(TimeInterval(hours * 3600))
        return all.filter { $0.stop > Date() && $0.start < cutoff }
    }

    /// FX-004: Programmes for a channel within an explicit date range.
    func schedule(for channel: Channel, from: Date, to: Date) -> [Programme] {
        store?.schedule(for: channel.tvgId, from: from, to: to) ?? []
    }

    // MARK: - Sport helpers

    func hasContent(for sport: SportCategory, in channels: [Channel]) -> Bool {
        liveCount(for: sport, in: channels) > 0 || upcomingCount(for: sport, in: channels) > 0
    }

    func liveCount(for sport: SportCategory, in channels: [Channel]) -> Int {
        channels.filter { channel in
            guard let prog = currentProgramme(for: channel) else { return false }
            return matchesSport(sport, programme: prog, channel: channel)
        }.count
    }

    func upcomingCount(for sport: SportCategory, in channels: [Channel]) -> Int {
        channels.filter { channel in
            guard let prog = nextProgramme(for: channel) else { return false }
            return matchesSport(sport, programme: prog, channel: channel)
        }.count
    }

    func activeSports(in channels: [Channel]) -> [SportCategory] {
        SportCategory.allCases
            .filter { hasContent(for: $0, in: channels) }
            .sorted { liveCount(for: $0, in: channels) > liveCount(for: $1, in: channels) }
    }

    // FX-009: check title against keywords
    func matchesSport(_ sport: SportCategory, programme: Programme, channel: Channel) -> Bool {
        let title = programme.title.lowercased()
        return sport.epgKeywords.contains { title.contains($0) }
    }
}
