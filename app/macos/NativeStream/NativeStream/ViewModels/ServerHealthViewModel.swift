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

        let healthURL = serverURL.appendingPathComponent("api/health")
        do {
            let (data, response) = try await URLSession.shared.data(from: healthURL)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                status = .unreachable
                return
            }
            let json = try JSONDecoder().decode(HealthResponse.self, from: data)
            status = .connected(channels: json.channels, healthy: json.healthy)
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

    func stopPolling() {
        checkTask?.cancel()
    }

    // MARK: - Helpers

    var isConnected: Bool {
        if case .connected = status { return true }
        return false
    }

    private struct HealthResponse: Decodable {
        let channels: Int
        let healthy: Int
    }
}