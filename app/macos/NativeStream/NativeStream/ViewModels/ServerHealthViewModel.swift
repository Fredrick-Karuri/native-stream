// ServerHealthViewModel.swift — NS-160, NS-161
// Monitors StreamServer connectivity and exposes status to the UI.

import Foundation
import Observation

enum ServerStatus {
    case unknown
    case connected(channels: Int, healthy: Int)
    case unreachable
}

@Observable
@MainActor
final class ServerHealthViewModel {

    var status: ServerStatus = .unknown
    var isChecking = false

    private var checkTask: Task<Void, Never>?

    func check(serverURL: URL) async {
        isChecking = true
        defer { isChecking = false }

        do {
            let health = try await APIClient.shared.health()
            status = .connected(channels: health.channels, healthy: health.healthy)
        } catch {
            status = .unreachable
        }
    }

    func startPolling(serverURL: URL, interval: TimeInterval = 30) {
        checkTask?.cancel()
        checkTask = Task {
            while !Task.isCancelled {
                await check(serverURL: serverURL)
                try? await Task.sleep(for: .seconds(interval))
            }
        }
    }

    func stopPolling() {checkTask?.cancel()}

    // MARK: - Helpers

    var isConnected: Bool {
        if case .connected = status { return true }
        return false
    }

}
