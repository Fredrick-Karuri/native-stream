/// Services/ControlSession.swift
///
/// WebSocket client for Local Media Connect control plane.
/// Connects to ws://server/ws, registers as target, and exposes
/// incoming messages via AsyncStream. Reconnects automatically on
/// unexpected close with exponential backoff.

import Foundation
import Observation
import SwiftProtobuf
import SdkGenSwift

private let pingIntervalSeconds: TimeInterval = 30
private let reconnectBaseDelaySeconds: TimeInterval = 1
private let reconnectMaxDelaySeconds: TimeInterval = 30

@Observable
@MainActor
final class ControlSession {

    var connected: Bool = false

    private var task: URLSessionWebSocketTask?
    private var session = URLSession(configuration: .default)

    @ObservationIgnored
    private var continuation: AsyncStream<Stream_V1_Envelope>.Continuation?

    @ObservationIgnored
    let incomingMessages: AsyncStream<Stream_V1_Envelope>

    private var explicitDisconnect = false
    private var reconnectDelay: TimeInterval = reconnectBaseDelaySeconds
    private var lastServerURL: URL?
    private var lastDeviceID = ""
    private var lastDeviceName = ""

    init() {
        var cont: AsyncStream<Stream_V1_Envelope>.Continuation!
        self.incomingMessages = AsyncStream { continuation in
            cont = continuation
        }
        self.continuation = cont
    }

    func connect(serverURL: URL, deviceID: String, deviceName: String) async {
        explicitDisconnect = false
        lastServerURL  = serverURL
        lastDeviceID   = deviceID
        lastDeviceName = deviceName

        guard let wsURL = makeWebSocketURL(from: serverURL) else { return }

        let newTask = session.webSocketTask(with: wsURL)
        task = newTask
        newTask.resume()

        await register(deviceID: deviceID, name: deviceName)
        connected = true
        receive()
    }

    func send(_ envelope: Stream_V1_Envelope) async {
        var options = JSONEncodingOptions()
        options.preserveProtoFieldNames = true
        guard let text = try? envelope.jsonString(options: options) else { return }
        try? await task?.send(.string(text))
    }

    func disconnect() {
        explicitDisconnect = true
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        connected = false
    }

    // MARK: - Private

    private func register(deviceID: String, name: String) async {
        var payload = Stream_V1_RegisterPayload()
        payload.name = name
        payload.kind = .target
        guard let envelope = Stream_V1_Envelope.encoding(
            type: .register, from: deviceID, to: "server", payload: payload
        ) else { return }
        await send(envelope)
    }
    private func receive() {
        task?.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                switch result {
                case .success(let message):
                    self.handleMessage(message)
                    self.receive() // continue loop
                case .failure(let error):
                    self.connected = false
                    print("[ControlSession] receive failed: \(error)")
                    if !self.explicitDisconnect { self.scheduleReconnect() }
                }
            }
        }
    }

    func handleMessage(_ message: URLSessionWebSocketTask.Message) {
        guard case .string(let text) = message,
              let envelope = try? Stream_V1_Envelope(jsonString: text)
        else { return }
        continuation?.yield(envelope)
    }

    private func scheduleReconnect() {
        guard let serverURL = lastServerURL else { return }
        Task {
            try? await Task.sleep(for: .seconds(reconnectDelay))
            reconnectDelay = min(reconnectDelay * 2, reconnectMaxDelaySeconds)
            await connect(serverURL: serverURL, deviceID: lastDeviceID, deviceName: lastDeviceName)
        }
    }

    func makeWebSocketURL(from httpURL: URL) -> URL? {
        var components = URLComponents(url: httpURL, resolvingAgainstBaseURL: false)
        components?.scheme = "ws"
        components?.path = "/ws"
        return components?.url
    }
}
