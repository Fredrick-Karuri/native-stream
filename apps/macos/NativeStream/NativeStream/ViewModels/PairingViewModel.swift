// PairingViewModel.swift
//
// Drives the device-pairing handshake: starts a session, shows
// the code, polls until approved/expired, and on approval persists the
// token via SecureTokenStore. Owns its own poll Task so ServerStep doesn't
// need to manage timers directly — start() and stop() are the whole
// surface a view needs.

import Foundation
import Observation
import SdkGenSwift

@Observable
final class PairingViewModel {

    enum PairingState: Equatable {
        case idle
        case starting
        case waitingForApproval(code: String)
        case approved
        case expired
        case denied
        case failed(String)
    }

    private(set) var state: PairingState = .idle

    private let apiClient: APIClient
    private let tokenStore: SecureTokenStore
    private let onApproved: (String) -> Void

    private var pollTask: Task<Void, Never>?
    private var currentSessionID: String?

    private static let pollInterval: Duration = .seconds(2)
    private static let devicePlatformLabel = "macOS"

    /// onApproved is called with the minted token once pairing succeeds,
    /// so the caller (typically ServerStep) can react — e.g. advance the
    /// onboarding flow — without PairingViewModel knowing about navigation.
    init(
        apiClient: APIClient = .shared,
        tokenStore: SecureTokenStore = SecureTokenStore(),
        onApproved: @escaping (String) -> Void
    ) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
        self.onApproved = onApproved
    }

    /// Starts a new pairing session and begins polling. Safe to call again
    /// after expiry — cancels any in-flight poll loop first.
    func start() {
        stop()
        state = .starting

        pollTask = Task { [weak self] in
            await self?.runPairingFlow()
        }
    }

    /// Cancels any in-flight pairing session poll. Called on view
    /// disappearance and internally before starting a fresh session.
    func stop() {
        pollTask?.cancel()
        pollTask = nil
    }

    private func runPairingFlow() async {
        do {
            let session = try await apiClient.startPairing(platform: Self.devicePlatformLabel)
            currentSessionID = session.sessionID
            state = .waitingForApproval(code: session.code)
        } catch {
            state = .failed("Could not start pairing: \(error.localizedDescription)")
            return
        }

        await pollUntilResolved()
    }

    private func pollUntilResolved() async {
        guard let sessionID = currentSessionID else { return }

        while !Task.isCancelled {
            do {
                let status = try await apiClient.pairingStatus(sessionID: sessionID)
                switch status.status {
                case "approved":
                    tokenStore.setAPIToken(status.token)
                    state = .approved
                    onApproved(status.token)
                    return
                case "expired":
                    state = .expired
                    return
                case "denied":
                    state = .denied
                    return
                default:
                    break // still pending — keep polling
                }
            } catch {
                // A transient network error while polling shouldn't kill
                // the whole flow — keep polling until expiry catches it,
                // rather than surfacing a scary error for one dropped
                // request.
            }

            try? await Task.sleep(for: Self.pollInterval)
        }
    }
}
