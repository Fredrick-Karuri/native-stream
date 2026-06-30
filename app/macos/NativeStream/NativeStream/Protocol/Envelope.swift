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

enum JSONValue: Codable {
    case object([String: JSONValue])
    case array([JSONValue])
    case string(String)
    case number(Double)
    case bool(Bool)
    case null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let v = try? c.decode([String: JSONValue].self) { self = .object(v) }
        else if let v = try? c.decode([JSONValue].self) { self = .array(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else { self = .null }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }
}

struct Envelope: Codable {
    let type: MessageType
    let from: String
    let to: String
    let auth: String?
    let payload: JSONValue

    init(type: MessageType, from: String, to: String, auth: String? = nil, payload: JSONValue = .object([:])) {
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
    let channelName: String
    let streamURL: String
    let playing: Bool
    let connectedAt: String

    enum CodingKeys: String, CodingKey {
        case deviceID    = "device_id"
        case name, kind
        case channelID   = "channel_id"
        case channelName = "channel_name"
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
    let channelName: String
    let streamURL: String

    enum CodingKeys: String, CodingKey {
        case channelID   = "channel_id"
        case channelName = "channel_name"
        case streamURL   = "stream_url"
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
    let channelName: String
    let streamURL: String

    enum CodingKeys: String, CodingKey {
        case channelID   = "channel_id"
        case channelName = "channel_name"
        case streamURL   = "stream_url"
    }
}

struct StateUpdatePayload: Codable {
    let channelID: String
    let channelName: String
    let streamURL: String
    let playing: Bool

    enum CodingKeys: String, CodingKey {
        case channelID   = "channel_id"
        case channelName = "channel_name"
        case streamURL   = "stream_url"
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
              let value = try? JSONDecoder().decode(JSONValue.self, from: data)
        else { return nil }
        return Envelope(type: type, from: from, to: to, payload: value)
    }

    func decoding<T: Decodable>(as type: T.Type) -> T? {
        guard let data = try? JSONEncoder().encode(payload) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
