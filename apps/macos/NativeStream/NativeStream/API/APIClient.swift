// APIClient.swift
// Typed networking layer for all NativeStream Server endpoints.
// All methods are async throws. Errors are mapped to APIError.

import Foundation
import SwiftProtobuf
import SdkGenSwift


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
    
    private func rawProtoBody(
        method: String, path: String, message: any SwiftProtobuf.Message
    ) async throws -> Data {
        let url = try resolve(path)
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var options = JSONEncodingOptions()
        options.preserveProtoFieldNames = true
        req.httpBody = try message.jsonUTF8Data(options: options)
        return try await execute(req)
    }

    init(
        baseURL: URL = URL(string: "http://localhost:8888")!,
        protocolClasses: [AnyClass]? = nil
    ) {
        self.baseURL = baseURL
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest  = 10
        config.timeoutIntervalForResource = 30
        config.urlCache = URLCache(
            memoryCapacity: 10 * 1024 * 1024,  // 10 MB
            diskCapacity:   50 * 1024 * 1024,  // 50 MB
            diskPath:       "nativestream_api_cache"
        )
        config.requestCachePolicy = .useProtocolCachePolicy
        if let protocolClasses {
            config.protocolClasses = protocolClasses
        }
        self.session = URLSession(configuration: config)
    }

    func setBaseURL(_ url: URL) {
        baseURL = url
    }

    // MARK: - Health

    func health() async throws -> Stream_V1_HealthResponse {
        let data = try await rawGet("api/health")
        do {
            return try Stream_V1_HealthResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    // MARK: - Playlist & EPG (raw Data — parsed by existing parsers)

    func playlistData() async throws -> Data {
        try await rawGet("playlist.m3u")
    }
    
    func probePlaylistForEpg(url: URL) async -> URL? {
        guard let data = try? await fetchRawURL(url),
              let text = String(data: data.prefix(8192), encoding: .utf8) ??
                         String(data: data.prefix(8192), encoding: .isoLatin1) else { return nil }

        let header = text.components(separatedBy: .newlines).first ?? text

        for key in ["url-tvg", "x-tvg-url"] {
            if let value = M3UParser.extractAttribute(key, from: header) {
                return URL(string: value)
            }
        }
        return nil
    }
    
    func fetchRawURL(_ url: URL) async throws -> Data {
        var req = URLRequest(url: url)
        req.cachePolicy = .useProtocolCachePolicy
        return try await execute(req)
    }

    func epgData() async throws -> Data {
        try await rawGet("epg.xml")
    }

    // MARK: - Channels

    func listChannels() async throws -> [Stream_V1_ChannelResponse] {
        let data = try await rawGet("api/channels")
        do {
            return try Stream_V1_ChannelListResponse(jsonUTF8Data: data).channels
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func getChannel(id: String) async throws -> Stream_V1_ChannelDetailResponse {
        let data = try await rawGet("api/channels/\(id)")
        do {
            return try Stream_V1_ChannelDetailResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func createChannel(_ req: Stream_V1_CreateChannelRequest) async throws -> Stream_V1_ChannelDetailResponse {
        let data = try await rawProtoBody(method: "POST", path: "api/channels", message: req)
        do {
            return try Stream_V1_ChannelDetailResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func updateChannel(id: String, _ req: Stream_V1_UpdateChannelRequest) async throws {
        let data = try await rawProtoBody(method: "PUT", path: "api/channels/\(id)", message: req)
        do {
            _ = try Stream_V1_StatusResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func deleteChannel(id: String) async throws {
        let url = try resolve("api/channels/\(id)")
        var req = URLRequest(url: url)
        req.httpMethod = "DELETE"
        let data = try await execute(req)
        do {
            _ = try Stream_V1_StatusResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }
    
    // MARK: - Discovery

    func discoveryStatus() async throws -> Stream_V1_DiscoveryStatusResponse {
        let data = try await rawGet("api/discovery/status")
        do {
            return try Stream_V1_DiscoveryStatusResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func triggerDiscovery() async throws {
        let url = try resolve("api/discovery/run")
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        let data = try await execute(req)
        do {
            _ = try Stream_V1_StatusResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func unmatchedLinks(limit: Int = 50) async throws -> Stream_V1_UnmatchedResponse {
        let data = try await rawGet("api/discovery/unmatched?limit=\(limit)")
        do {
            return try Stream_V1_UnmatchedResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
    }

    func assignUnmatchedLink(channelID: String, url: String) async throws {
        var req = Stream_V1_UpdateChannelRequest()
        req.streamURL = url
        try await updateChannel(id: channelID, req)
    }

    // MARK: - Probe

    func triggerProbe() async throws {
        let _: StatusResponse = try await post("api/probe", body: EmptyBody())
    }
    
    // MARK: - Proxy config

    func getProxyEnabled() async throws -> Bool {
        let data = try await rawGet("api/proxy/config")
        do {
            return try Stream_V1_ProxyConfigResponse(jsonUTF8Data: data).enabled
        } catch {
            throw APIError.decodingFailed(error)
        }
    }
    
    func setProxyEnabled(_ enabled: Bool) async throws {
        var req = Stream_V1_UpdateProxyConfigRequest()
        req.enabled = enabled
        let data = try await rawProtoBody(method: "PUT", path: "api/proxy/config", message: req)
        do {
            _ = try Stream_V1_ProxyConfigResponse(jsonUTF8Data: data)
        } catch {
            throw APIError.decodingFailed(error)
        }
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
        var req = URLRequest(url: url)
        req.cachePolicy = .useProtocolCachePolicy
        req.setValue("max-age=7200, public", forHTTPHeaderField: "Cache-Control")
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
