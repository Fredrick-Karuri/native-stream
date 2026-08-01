// File: ControlAction.swift
//
// Pure decision logic extracted from ControlViewModel.handle(_:playerVM:).
// Given an incoming Envelope, decides what ControlViewModel should do — with
// no dependency on ControlSession or PlayerViewModel. This is what makes the
// routing testable: ControlViewModel.handle now just asks this what to do
// and executes the corresponding side effect.

import Foundation
import SdkGenSwift

enum ControlAction: Equatable {
    case play(Channel)
    case stop
    case setVolume(Float)
    case updateSessions([Stream_V1_SessionInfo])
    case sendPong
    case none
}

enum ControlActionDecider {
    static func decide(for envelope: Stream_V1_Envelope) -> ControlAction {
        switch envelope.type {
        case .play:
            guard let payload = envelope.decoding(as: Stream_V1_PlayPayload.self) else { return .none }
            return .play(channel(from: payload))
        case .stop:
            return .stop
        case .volumeSet:
            guard let payload = envelope.decoding(as: Stream_V1_VolumeSetPayload.self) else { return .none }
            return .setVolume(Float(payload.level))
        case .sessionList:
            guard let payload = envelope.decoding(as: Stream_V1_SessionListPayload.self) else { return .none }
            return .updateSessions(payload.sessions.filter { $0.kind == .controller })
        case .ping:
            return .sendPong
        default:
            return .none
        }
    }

    static func channel(from payload: Stream_V1_PlayPayload) -> Channel {
        Channel(
            tvgId: "",
            name: payload.channelName,
            groupTitle: "Remote",
            streamURL: URL(string: payload.streamURL) ?? URL(string: "about:blank")!,
            streamHeaders: [:]
        )
    }
}
