// APIClient.swift
// Typed networking layer for all NativeStream Server endpoints.
// All methods are async throws. Errors are mapped to APIError.

import Foundation

// MARK: - Errors

enum APIError: Error, LocalizedError {
    case serverUnreachable(URL)
    case httpError(Int, String?)
    case decodingFailed(Error)
    case invalidURL(String)
    case noActiveLink

    var errorDescription: String? {
        switch self {
        case .serverUnreachable(let url):
            return "Server unreachable at \(url.host ?? url.absoluteString)"
        case .httpError(let code, let msg):
            return "Server returned \(code)\(msg.map { ": \($0)" } ?? "")"
        case .decodingFailed(let err):
            return "Response decode failed: \(err.localizedDescription)"
        case .invalidURL(let s):
            return "Invalid URL: \(s)"
        case .noActiveLink:
            return "Channel has no active stream link"
        }
    }
}

// MARK: - Response models

struct HealthResponse: Decodable {
    let status: String
    let uptime: String
    let channels: Int
    let healthy: Int
    let lastProbe: Date?

    enum CodingKeys: String, CodingKey {
        case status, uptime, channels, healthy
        case lastProbe = "last_probe"
    }
}

struct ChannelResponse: Decodable, Identifiable {
    let id: String
    let name: String
    let groupTitle: String
    let tvgID: String
    let logoURL: String
    let healthy: Bool
    let activeScore: Double
    let candidateCount: Int

    enum CodingKeys: String, CodingKey {
        case id, name, healthy
        case groupTitle    = "group_title"
        case tvgID         = "tvg_id"
        case logoURL       = "logo_url"
        case activeScore   = "active_score"
        case candidateCount = "candidate_count"
    }
}

struct ChannelListResponse: Decodable {
    let channels: [ChannelResponse]
}

struct ChannelDetailResponse: Decodable {
    let id: String
    let name: String
    let groupTitle: String
    let tvgID: String
    let logoURL: String
    let keywords: [String]
    let activeLink: LinkScoreResponse?
    let candidates: [LinkScoreResponse]

    enum CodingKeys: String, CodingKey {
        case id, name, keywords, candidates
        case groupTitle = "group_title"
        case tvgID      = "tvg_id"
        case logoURL    = "logo_url"
        case activeLink = "active_link"
    }
}

struct LinkScoreResponse: Decodable {
    let url: String
    let score: Double
    let latencyMS: Int
    let state: String
    let failCount: Int

    enum CodingKeys: String, CodingKey {
        case url, score, state
        case latencyMS  = "latency_ms"
        case failCount  = "fail_count"
    }
}

struct CreateChannelRequest: Encodable {
    let name: String
    let groupTitle: String
    let tvgID: String
    let logoURL: String
    let streamURL: String
    let keywords: [String]

    enum CodingKeys: String, CodingKey {
        case name, keywords
        case groupTitle = "group_title"
        case tvgID      = "tvg_id"
        case logoURL    = "logo_url"
        case streamURL  = "stream_url"
    }
}

struct UpdateChannelRequest: Encodable {
    var name: String?
    var groupTitle: String?
    var streamURL: String?
    var keywords: [String]?

    enum CodingKeys: String, CodingKey {
        case name, keywords
        case groupTitle = "group_title"
        case streamURL  = "stream_url"
    }
}

struct DiscoveryStatusResponse: Decodable {
    let lastRun: Date?
    let foundToday: Int
    let promotedToday: Int
    let unmatchedCount: Int

    enum CodingKeys: String, CodingKey {
        case lastRun       = "last_run"
        case foundToday    = "found_today"
        case promotedToday = "promoted_today"
        case unmatchedCount = "unmatched_count"
    }
}

struct UnmatchedLink: Decodable, Identifiable {
    let url: String
    let sourceURL: String
    let context: String

    var id: String { url }

    enum CodingKeys: String, CodingKey {
        case url, context
        case sourceURL = "source_url"
    }
}

struct UnmatchedResponse: Decodable {
    let unmatched: [UnmatchedLink]
    let total: Int
}

struct StatusResponse: Decodable {
    let status: String
}

// MARK: - APIClient

actor APIClient {

    static let shared = APIClient()

    private var baseURL: URL
    private let session: URLSession

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    init(baseURL: URL = URL(string: "http://localhost:8888")!) {
        self.baseURL = baseURL
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest  = 10
        config.timeoutIntervalForResource = 30
        self.session = URLSession(configuration: config)
    }

    func setBaseURL(_ url: URL) {
        baseURL = url
    }

    // MARK: - Health

    func health() async throws -> HealthResponse {
        try await get("api/health")
    }

    // MARK: - Playlist & EPG (raw Data — parsed by existing parsers)

    func playlistData() async throws -> Data {
        try await rawGet("playlist.m3u")
    }

    func epgData() async throws -> Data {
        try await rawGet("epg.xml")
    }

    // MARK: - Channels

    func listChannels() async throws -> [ChannelResponse] {
        let r: ChannelListResponse = try await get("api/channels")
        return r.channels
    }

    func getChannel(id: String) async throws -> ChannelDetailResponse {
        try await get("api/channels/\(id)")
    }

    func createChannel(_ req: CreateChannelRequest) async throws -> ChannelDetailResponse {
        try await post("api/channels", body: req)
    }

    func updateChannel(id: String, _ req: UpdateChannelRequest) async throws {
        let _: StatusResponse = try await put("api/channels/\(id)", body: req)
    }

    func deleteChannel(id: String) async throws {
        let _: StatusResponse = try await delete("api/channels/\(id)")
    }

    // MARK: - Discovery

    func discoveryStatus() async throws -> DiscoveryStatusResponse {
        try await get("api/discovery/status")
    }

    func triggerDiscovery() async throws {
        let _: StatusResponse = try await post("api/discovery/run", body: EmptyBody())
    }

    func unmatchedLinks(limit: Int = 50) async throws -> UnmatchedResponse {
        try await get("api/discovery/unmatched?limit=\(limit)")
    }

    func assignUnmatchedLink(channelID: String, url: String) async throws {
        try await updateChannel(id: channelID, UpdateChannelRequest(streamURL: url))
    }

    // MARK: - Probe

    func triggerProbe() async throws {
        let _: StatusResponse = try await post("api/probe", body: EmptyBody())
    }

    // MARK: - Private HTTP primitives

    private func get<T: Decodable>(_ path: String) async throws -> T {
        let data = try await rawGet(path)
        return try decode(T.self, from: data)
    }

    private func post<Body: Encodable, Response: Decodable>(
        _ path: String, body: Body
    ) async throws -> Response {
        let data = try await rawBody(method: "POST", path: path, body: body)
        return try decode(Response.self, from: data)
    }

    private func put<Body: Encodable, Response: Decodable>(
        _ path: String, body: Body
    ) async throws -> Response {
        let data = try await rawBody(method: "PUT", path: path, body: body)
        return try decode(Response.self, from: data)
    }

    private func delete<Response: Decodable>(_ path: String) async throws -> Response {
        let url = try resolve(path)
        var req = URLRequest(url: url)
        req.httpMethod = "DELETE"
        let data = try await execute(req)
        return try decode(Response.self, from: data)
    }

    private func rawGet(_ path: String) async throws -> Data {
        let url = try resolve(path)
        let req = URLRequest(url: url)
        return try await execute(req)
    }

    private func rawBody<Body: Encodable>(
        method: String, path: String, body: Body
    ) async throws -> Data {
        let url = try resolve(path)
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try APIClient.encoder.encode(body)
        return try await execute(req)
    }

    private func execute(_ request: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.serverUnreachable(request.url ?? baseURL)
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.serverUnreachable(request.url ?? baseURL)
        }
        guard (200...299).contains(http.statusCode) else {
            let msg = String(data: data, encoding: .utf8)
            throw APIError.httpError(http.statusCode, msg)
        }
        return data
    }

    private func resolve(_ path: String) throws -> URL {
        guard let url = URL(string: path, relativeTo: baseURL)?.absoluteURL else {
            throw APIError.invalidURL(path)
        }
        return url
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try APIClient.decoder.decode(type, from: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }
}

// MARK: - Helpers

private struct EmptyBody: Encodable {}
