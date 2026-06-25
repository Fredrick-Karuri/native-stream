//
//  ApiDtos.swift
//

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
    let serverName: String?
    let addr: String?

    enum CodingKeys: String, CodingKey {
        case status, uptime, channels, healthy, addr
        case lastProbe   = "last_probe"
        case serverName  = "server_name"
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
    let headers: [String: String]?

    enum CodingKeys: String, CodingKey {
        case url, score, state, headers
        case latencyMS = "latency_ms"
        case failCount = "fail_count"
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
