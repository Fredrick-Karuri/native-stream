// EPGViewModel.swift
import Foundation
import Observation
import Compression

@Observable
@MainActor
final class EPGViewModel {

    var stores: [UUID: EPGStore] = [:]
    var isLoading: Bool = false
    var isAvailable: Bool = true
    var epgURL: URL? = nil

    private let settingsKey = UUID(uuidString: "00000000-0000-0000-0000-000000000000")!
    
    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.urlCache = URLCache(
            memoryCapacity: 10 * 1024 * 1024,
            diskCapacity:   50 * 1024 * 1024,
            diskPath:       "nativestream_epg_cache"
        )
        config.requestCachePolicy = .useProtocolCachePolicy
        return URLSession(configuration: config)
    }()

    // MARK: - Load

    func load(sources: [PlaylistSource] = []) async {
        isLoading = true
        defer { isLoading = false }

        // Deduplicate: don't fetch a source URL that matches the settings URL
        let settingsURL = epgURL.map { Self.normalizeEPGURL($0) }
        let uniqueSources = sources.filter { source in
            guard let url = source.epgURL else { return false }
            return Self.normalizeEPGURL(url) != settingsURL
        }
        var collected: [UUID: EPGStore] = [:]


        await withTaskGroup(of: (id: UUID, store: EPGStore?).self) { group in
            for source in uniqueSources {
                guard let url = source.epgURL else { continue }
                group.addTask {
                    let parser = EPGParser()   // ← own instance per task
                    do {
                        let store = try await Self.fetchAndParse(url: url, parser: parser)
                        return (source.id, store)
                    } catch {
                        print("⚠️ [EPG] Failed for \(url): \(error)")
                        return (source.id, nil)
                    }
                }
            }

            if let url = epgURL {
                group.addTask {
                    let parser = EPGParser()
                    let store = try? await Self.fetchAndParse(url: url, parser: parser)
                    return (self.settingsKey, store)
                }
            }

            for await result in group {
                if let store = result.store {
                    
                    collected[result.id] = store
                }
            }
            stores = collected
        }

        isAvailable = !stores.isEmpty
        let total    = stores.values.reduce(0) { $0 + $1.programmeCount }
        let channels = stores.values.reduce(0) { $0 + $1.channelCount }
        print("✅ [EPG] \(total) programmes, \(channels) channels across \(stores.count) store(s)")
    }

    // MARK: - Fetch + parse

    private nonisolated static func fetchAndParse(url: URL, parser: EPGParser) async throws -> EPGStore {
        let normalized = normalizeEPGURL(url)
        let (data, response) = try await EPGViewModel.session.data(from: normalized)

        guard let http = response as? HTTPURLResponse,
              (200...299).contains(http.statusCode) else {
            throw AppError.epgFetchFailed(underlying: URLError(.badServerResponse))
        }
        print("📦 [EPG] data size: \(data.count), first 4 bytes: \(data.prefix(4).map { String(format: "%02x", $0) }.joined())")

        let decompressed: Data
        if normalized.pathExtension == "gz" {
            guard data.count > 18 else { throw AppError.epgParseError(reason: "GZ data too short") }
            guard let payload = stripGzipHeader(data) else {
                throw AppError.epgParseError(reason: "GZ decompression failed")
            }
            let bufferSize = 10_000_000
            var out = Data(count: bufferSize)
            let written = out.withUnsafeMutableBytes { outPtr in
                payload.withUnsafeBytes { inPtr in
                    compression_decode_buffer(
                        outPtr.bindMemory(to: UInt8.self).baseAddress!,
                        bufferSize,
                        inPtr.bindMemory(to: UInt8.self).baseAddress!,
                        payload.count, nil, COMPRESSION_ZLIB
                    )
                }
            }
            guard written > 0 else { throw AppError.epgParseError(reason: "GZ decompression failed") }
            decompressed = out.prefix(written) as Data
        } else {
            decompressed = data
        }

        return try await Task.detached(priority: .userInitiated) {
            try parser.parse(data: decompressed)
        }.value
    }

    nonisolated static func normalizeEPGURL(_ url: URL) -> URL {
        guard url.host == "github.com" else { return url }
        let fixed = url.absoluteString
            .replacingOccurrences(of: "https://github.com/", with: "https://raw.githubusercontent.com/")
            .replacingOccurrences(of: "/raw/", with: "/")
        return URL(string: fixed) ?? url
    }
    
    private nonisolated static func stripGzipHeader(_ data: Data) -> Data? {
        guard data.count > 18 else { return nil }
        var offset = 10
        let flags = data[3]
        if flags & 0x04 != 0 { // FEXTRA
            guard offset + 2 <= data.count else { return nil }
            let xlen = Int(data[offset]) | Int(data[offset + 1]) << 8
            offset += 2 + xlen
        }
        if flags & 0x08 != 0 { // FNAME — skip null-terminated string
            while offset < data.count && data[offset] != 0 { offset += 1 }
            offset += 1
        }
        if flags & 0x10 != 0 { // FCOMMENT
            while offset < data.count && data[offset] != 0 { offset += 1 }
            offset += 1
        }
        if flags & 0x02 != 0 { offset += 2 } // FHCRC
        guard offset < data.count - 8 else { return nil }
        return data.subdata(in: offset..<data.count - 8)
    }

    // MARK: - Queries

    func currentProgramme(for channel: Channel) -> Programme? {
        stores.values.lazy.compactMap { $0.currentProgramme(for: channel.tvgId) }.first
    }

    func nextProgramme(for channel: Channel) -> Programme? {
        stores.values.lazy.compactMap { $0.nextProgramme(for: channel.tvgId) }.first
    }

    func schedule(for channel: Channel, hours: Int = 6) -> [Programme] {
        let cutoff = Date().addingTimeInterval(TimeInterval(hours * 3600))
        var seen = Set<String>()
        return stores.values
            .flatMap { $0.schedule(for: channel.tvgId) }
            .filter { $0.stop > Date() && $0.start < cutoff }
            .filter { seen.insert($0.id).inserted }
            .sorted { $0.start < $1.start }
    }

    func schedule(for channel: Channel, from: Date, to: Date) -> [Programme] {
        stores.values
            .flatMap { $0.schedule(for: channel.tvgId, from: from, to: to) }
            .sorted { $0.start < $1.start }
    }

    // MARK: - Diagnostic

    func logMatchDiagnostic(for channels: [Channel]) {
        guard !channels.isEmpty else { return }
        let matched = channels.filter { ch in
            stores.values.contains { $0.currentProgramme(for: ch.tvgId) != nil }
        }.count
        print("📺 [EPG] Match rate: \(Int(Double(matched) / Double(channels.count) * 100))% (\(matched)/\(channels.count))")
    }

    // MARK: - Sport helpers

    func hasContent(for sport: SportCategory, in channels: [Channel]) -> Bool {
        liveCount(for: sport, in: channels) > 0 || upcomingCount(for: sport, in: channels) > 0
    }

    func liveCount(for sport: SportCategory, in channels: [Channel]) -> Int {
        channels.filter { ch in
            guard let prog = currentProgramme(for: ch) else { return false }
            return matchesSport(sport, programme: prog, channel: ch)
        }.count
    }

    func upcomingCount(for sport: SportCategory, in channels: [Channel]) -> Int {
        channels.filter { ch in
            guard let prog = nextProgramme(for: ch) else { return false }
            return matchesSport(sport, programme: prog, channel: ch)
        }.count
    }

    func activeSports(in channels: [Channel]) -> [SportCategory] {
        SportCategory.allCases
            .filter { hasContent(for: $0, in: channels) }
            .sorted { liveCount(for: $0, in: channels) > liveCount(for: $1, in: channels) }
    }

    func matchesSport(_ sport: SportCategory, programme: Programme, channel: Channel) -> Bool {
        let title = programme.title.lowercased()
        return sport.epgKeywords.contains { title.contains($0) }
    }
}
