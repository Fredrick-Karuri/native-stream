// ViewModels/ControlViewModel.swift
///
/// Owns the LMC control session lifecycle for the macOS target role.
/// Receives play/stop commands from controllers, broadcasts state_update
/// on every playback change, and exposes connected controller sessions.

import Foundation
import Observation
import AVFoundation
import SdkGenSwift

@Observable
@MainActor
final class ControlViewModel {

    var sessions: [Stream_V1_SessionInfo] = []
    var lastPlayWasRemote: Bool = false
    var connected: Bool { controlSession.connected }

    private let controlSession: ControlSession
    private var deviceID = ""
    private var listenTask: Task<Void, Never>?

    init(controlSession: ControlSession) {
        self.controlSession = controlSession
    }

    func start(serverURL: URL, deviceID: String, playerVM: PlayerViewModel) {
        self.deviceID = deviceID
        listenTask?.cancel()
        listenTask = Task {
            await controlSession.connect(
                serverURL: serverURL,
                deviceID: deviceID,
                deviceName: Host.current().localizedName ?? "Mac"
            )
            for await envelope in controlSession.incomingMessages {
                await handle(envelope, playerVM: playerVM)
            }
        }
    }

    func stop() {
        listenTask?.cancel()
        controlSession.disconnect()
    }

    func broadcastState(channelID: String, channelName: String, streamURL: String, playing: Bool, volume: Double) async {
        var payload = Stream_V1_StateUpdatePayload()
        payload.channelID = channelID
        payload.channelName = channelName
        payload.streamURL = streamURL
        payload.playing = playing
        payload.volume = volume
        guard let envelope = Stream_V1_Envelope.encoding(
            type: .stateUpdate, from: deviceID, to: "broadcast", payload: payload
        ) else { return }
        await controlSession.send(envelope)
    }
    
    // MARK: - Private

    private func handle(_ envelope: Stream_V1_Envelope, playerVM: PlayerViewModel) async {
        switch ControlActionDecider.decide(for: envelope) {
        case .play(let channel):
            lastPlayWasRemote = true
            try? await playerVM.play(channel: channel)
        case .stop:
            playerVM.stop()
        case .setVolume(let level):
            playerVM.player?.volume = level
        case .updateSessions(let sessions):
            self.sessions = sessions
        case .sendPong:
            await sendPong()
        case .none:
            break
        }
    }

    private func sendPong() async {
        var envelope = Stream_V1_Envelope()
        envelope.type = .pong
        envelope.from = deviceID
        envelope.to = "server"
        await controlSession.send(envelope)
    }
}

private struct EmptyPayload: Codable {}
