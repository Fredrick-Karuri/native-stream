/// Protocol/Envelope.swift
///
/// LMC message protocol types — mirrors server/control/protocol.go.
/// All WebSocket messages between macOS and the Go broker use these types.

import Foundation

enum MessageType: String, Codable {
    case register
    case sessionList    = "session_list"
    case play
    case stop
    case pullBack        = "pull_back"
    case pullBackAck      = "pull_back_ack"
    case stateUpdate     = "state_update"
    case ping
    case pong
}

enum DeviceKind: String, Codable {
    case controller
    case target
    case tv // reserved for future TV client
}

struct Envelope: Codable {
    let type: MessageType
    let from: String
    let to: String
    let auth: String?
    let payload: String

    init(type: MessageType, from: String, to: String, auth: String? = nil, payload: String = "{}") {
        self.type = type
        self.from = from
        self.to = to
        self.auth = auth
        self.payload = payload
    }
}

struct SessionInfo: Codable, Identifiable {
    var id: String { deviceID }
    let deviceID: String
    let name: String
    let kind: DeviceKind
    let channelID: String
    let streamURL: String
    let playing: Bool
    let connectedAt: String

    enum CodingKeys: String, CodingKey {
        case deviceID    = "device_id"
        case name, kind
        case channelID   = "channel_id"
        case streamURL   = "stream_url"
        case playing
        case connectedAt = "connected_at"
    }
}

// MARK: - Payload types

struct RegisterPayload: Codable {
    let name: String
    let kind: DeviceKind
}

struct PlayPayload: Codable {
    let channelID: String
    let streamURL: String

    enum CodingKeys: String, CodingKey {
        case channelID = "channel_id"
        case streamURL = "stream_url"
    }
}

struct PullBackPayload: Codable {
    let fromDevice: String

    enum CodingKeys: String, CodingKey {
        case fromDevice = "from_device"
    }
}

struct PullBackAckPayload: Codable {
    let channelID: String
    let streamURL: String

    enum CodingKeys: String, CodingKey {
        case channelID = "channel_id"
        case streamURL = "stream_url"
    }
}

struct StateUpdatePayload: Codable {
    let channelID: String
    let streamURL: String
    let playing: Bool

    enum CodingKeys: String, CodingKey {
        case channelID = "channel_id"
        case streamURL = "stream_url"
        case playing
    }
}

struct SessionListPayload: Codable {
    let sessions: [SessionInfo]
}

// MARK: - Envelope helpers

extension Envelope {
    static func encoding<T: Encodable>(
        type: MessageType, from: String, to: String, payload: T
    ) -> Envelope? {
        guard let data = try? JSONEncoder().encode(payload),
              let json = String(data: data, encoding: .utf8) else { return nil }
        return Envelope(type: type, from: from, to: to, payload: json)
    }

    func decoding<T: Decodable>(as type: T.Type) -> T? {
        guard let data = payload.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
