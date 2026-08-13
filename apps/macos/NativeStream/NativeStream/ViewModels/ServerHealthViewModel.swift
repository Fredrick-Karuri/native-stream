// ServerHealthViewModel.swift
// Monitors StreamServer connectivity and exposes status to the UI.

import Foundation
import Observation
import SdkGenSwift

enum ServerStatus {
    case unknown
    case connected(channels: Int, healthy: Int)
    case unreachable
    case authFailed
    case certificateInvalid
}

@Observable
@MainActor
final class ServerHealthViewModel {

    var status: ServerStatus = .unknown
    var isChecking = false
    var connectionState: OnboardingConnectionState = .idle

    func checkConnection(serverURL: URL) async {
        connectionState = .checking
        async let healthResult: Result<Stream_V1_HealthResponse, Error> = {
            do { return .success(try await APIClient.shared.health()) }
            catch { return .failure(error) }
        }()
        async let playlistTask = try? APIClient.shared.playlistData()
        async let epgTask      = try? APIClient.shared.epgData()

        let healthOutcome = await healthResult
        let playlist      = await playlistTask
        let epg           = await epgTask

        let health: Stream_V1_HealthResponse?
        let healthError: Error?
        switch healthOutcome {
        case .success(let value): health = value; healthError = nil
        case .failure(let error): health = nil; healthError = error
        }

        connectionState = ConnectionStateDecider.decide(
            health: health, playlist: playlist, epg: epg, healthError: healthError
        )    }

    func resetConnectionState() {
        connectionState = .idle
    }

    private var checkTask: Task<Void, Never>?

    func check(serverURL: URL) async {
        isChecking = true
        defer { isChecking = false }

        do {
            let health = try await APIClient.shared.health()
            status = .connected(channels: Int(health.channels), healthy: Int(health.healthy))
        } catch APIError.httpError(401, _) {
            status = .authFailed
        } catch let error as URLError where error.code
                    == .serverCertificateUntrusted
                    || error.code == .secureConnectionFailed {
            status = .certificateInvalid
        } catch {
            status = .unreachable
        }
    }

    func startPolling(serverURL: URL, interval: TimeInterval = 30) {
        checkTask?.cancel()
        checkTask = Task {
            while !Task.isCancelled {
                await check(serverURL: serverURL)
                if case .certificateInvalid = status { return }
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

    var isAuthFailed: Bool {
        if case .authFailed = status { return true }
        return false
    }
    var isCertificateInvalid: Bool {
        if case .certificateInvalid = status { return true }
        return false
    }
}
