// File: ControlAction.swift
//
// Pure decision logic extracted from ControlViewModel.handle(_:playerVM:).
// Given an incoming Envelope, decides what ControlViewModel should do — with
// no dependency on ControlSession or PlayerViewModel. This is what makes the
// routing testable: ControlViewModel.handle now just asks this what to do
// and executes the corresponding side effect.

import Foundation

enum ControlAction: Equatable {
    case play(Channel)
    case stop
    case setVolume(Float)
    case updateSessions([SessionInfo])
    case sendPong
    /// No handling defined for this envelope type (mirrors the original
    /// switch's `default: break`).
    case none
}

enum ControlActionDecider {

    /// Decides what action should result from an incoming envelope.
    /// Mirrors ControlViewModel.handle's switch exactly, including which
    /// payload-decode failures silently produce no action.
    static func decide(for envelope: Envelope) -> ControlAction {
        switch envelope.type {
        case .play:
            guard let payload = envelope.decoding(as: PlayPayload.self) else { return .none }
            return .play(channel(from: payload))
        case .stop:
            return .stop
        case .volumeSet:
            guard let payload = envelope.decoding(as: VolumeSetPayload.self) else { return .none }
            return .setVolume(Float(payload.level))
        case .sessionList:
            guard let payload = envelope.decoding(as: SessionListPayload.self) else { return .none }
            return .updateSessions(payload.sessions.filter { $0.kind == .controller })
        case .ping:
            return .sendPong
        default:
            return .none
        }
    }

    /// Builds the temporary remote-play Channel exactly as handlePlay does:
    /// no tvgId, groupTitle fixed to "Remote", falls back to "about:blank"
    /// if the payload's streamURL string doesn't parse.
    static func channel(from payload: PlayPayload) -> Channel {
        Channel(
            tvgId: "",
            name: payload.channelName,
            groupTitle: "Remote",
            streamURL: URL(string: payload.streamURL) ?? URL(string: "about:blank")!,
            streamHeaders: [:]
        )
    }
}
