// ViewModels/ControlViewModel.swift
///
/// Owns the LMC control session lifecycle for the macOS target role.
/// Receives play/stop commands from controllers, broadcasts state_update
/// on every playback change, and exposes connected controller sessions.

import Foundation
import Observation
import AVFoundation

@Observable
@MainActor
final class ControlViewModel {

    var sessions: [SessionInfo] = []
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
        guard let envelope = Envelope.encoding(
            type: .stateUpdate, from: deviceID, to: "broadcast",
            payload: StateUpdatePayload(channelID: channelID, channelName: channelName, streamURL: streamURL, playing: playing, volume: volume)
        ) else { return }
        await controlSession.send(envelope)
    }
    
    // MARK: - Private

    private func handle(_ envelope: Envelope, playerVM: PlayerViewModel) async {
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
        guard let envelope = Envelope.encoding(
            type: .pong, from: deviceID, to: "server", payload: EmptyPayload()
        ) else { return }
        await controlSession.send(envelope)
    }
}

private struct EmptyPayload: Codable {}
