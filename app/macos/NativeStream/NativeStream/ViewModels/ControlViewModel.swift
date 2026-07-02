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
        switch envelope.type {
        case .play:
            await handlePlay(envelope, playerVM: playerVM)
        case .stop:
            playerVM.stop()
        case .volumeSet:
            handleVolumeSet(envelope, playerVM: playerVM)
        case .sessionList:
            handleSessionList(envelope)
        case .ping:
            await sendPong()
        default:
            break
        }
    }

    private func handlePlay(_ envelope: Envelope, playerVM: PlayerViewModel) async {
        guard let payload = envelope.decoding(as: PlayPayload.self) else { return }
        lastPlayWasRemote = true
        let channel = Channel(
            tvgId: "",
            name: payload.channelName,
            groupTitle: "Remote",
            streamURL: URL(string: payload.streamURL) ?? URL(string: "about:blank")!,
            streamHeaders: [:]
        )
        try? await playerVM.play(channel: channel)
    }

    private func handleSessionList(_ envelope: Envelope) {
        guard let payload = envelope.decoding(as: SessionListPayload.self) else { return }
        sessions = payload.sessions.filter { $0.kind == .controller }
    }
    
    private func handleVolumeSet(_ envelope: Envelope, playerVM: PlayerViewModel) {
        guard let payload = envelope.decoding(as: VolumeSetPayload.self) else { return }
        playerVM.player?.volume = Float(payload.level)
    }

    private func sendPong() async {
        guard let envelope = Envelope.encoding(
            type: .pong, from: deviceID, to: "server", payload: EmptyPayload()
        ) else { return }
        await controlSession.send(envelope)
    }
}

private struct EmptyPayload: Codable {}
