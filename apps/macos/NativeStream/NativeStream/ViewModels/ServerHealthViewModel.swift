// ServerHealthViewModel.swift
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
    var connectionState: OnboardingConnectionState = .idle
    
    func checkConnection(serverURL: URL) async {
        connectionState = .checking
        async let healthTask   = try? APIClient.shared.health()
        async let playlistTask = try? APIClient.shared.playlistData()
        async let epgTask      = try? APIClient.shared.epgData()

        let health   = await healthTask
        let playlist = await playlistTask
        let epg      = await epgTask

        guard let h = health else {
            connectionState = .failure(.unreachable)
            return
        }
        guard let p = playlist, !p.isEmpty else {
            connectionState = .failure(.noPlaylist)
            return
        }
        let hasEpg = epg?.isEmpty == false
        connectionState = .success(
            channels:        h.channels,
            healthy:         h.healthy,
            hasEpg:          hasEpg,
            epgFromPlaylist: false
        )
    }

    func resetConnectionState() {
        connectionState = .idle
    }


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
