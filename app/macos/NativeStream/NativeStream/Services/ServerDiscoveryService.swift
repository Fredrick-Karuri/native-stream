//
//  ServerDiscoveryService.swift
//  NativeStream
//
//  Created by Fredrick Karuri on 25/06/2026.
//

///
/// Scans the local network for _nativestream._tcp via NWBrowser,
/// verifies candidates via /api/health, and emits a confirmed URL.

import Foundation
import Network
import Observation

private let serviceType            = "_nativestream._tcp"
private let healthCheckTimeoutSecs = 5.0

@Observable
@MainActor
final class ServerDiscoveryService {

    var discoveredURL: URL? = nil
    var isScanning: Bool    = false

    private var browser: NWBrowser?

    // MARK: - Public API

    func scan() {
        guard !isScanning else { return }
        discoveredURL = nil
        isScanning    = true

        let params  = NWParameters()
        params.includePeerToPeer = true
        let browser = NWBrowser(for: .bonjourWithTXTRecord(type: serviceType, domain: "local"), using: params)
        self.browser = browser

        browser.stateUpdateHandler = { [weak self] state in
            if case .failed = state {
                Task { @MainActor [weak self] in self?.stopScanning() }
            }
        }

        browser.browseResultsChangedHandler = { [weak self] results, _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                for result in results {
                    if case .service(let name, _, _, _) = result.endpoint {
                        self.resolveAndVerify(endpoint: result.endpoint, name: name)
                    }
                }
            }
        }

        browser.start(queue: .main)
    }

    func stopScanning() {
        browser?.cancel()
        browser    = nil
        isScanning = false
    }

    // MARK: - Private

    private func resolveAndVerify(endpoint: NWEndpoint, name: String) {
        let connection = NWConnection(to: endpoint, using: .tcp)
        connection.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            if case .ready = state {
                guard let host = connection.currentPath?.remoteEndpoint,
                      case .hostPort(let h, let port) = host else {
                    connection.cancel()
                    return
                }
                let hostString = "\(h)"
                    .replacingOccurrences(of: "%en0", with: "")
                    .replacingOccurrences(of: "%lo0", with: "")
                let portInt    = Int(port.rawValue)
                let candidate  = URL(string: "http://\(hostString):\(portInt)")
                connection.cancel()
                guard let candidate else { return }
                Task { await self.verifyCandidate(candidate) }
            } else if case .failed = state {
                connection.cancel()
            }
        }
        connection.start(queue: .main)
    }

    private func verifyCandidate(_ url: URL) async {
        let verified = await withTimeout(seconds: healthCheckTimeoutSecs) {
            do {
                let healthURL = url.appendingPathComponent("api/health")
                let (_, response) = try await URLSession.shared.data(from: healthURL)
                return (response as? HTTPURLResponse)?.statusCode == 200
            } catch {
                return false
            }
        }
        if verified == true {
            discoveredURL = url
            stopScanning()
        }
    }
}

// MARK: - Timeout helper

private func withTimeout<T: Sendable>(
    seconds: TimeInterval,
    operation: @escaping @Sendable () async -> T?
) async -> T? {
    await withTaskGroup(of: T?.self) { group in
        group.addTask { await operation() }
        group.addTask {
            try? await Task.sleep(for: .seconds(seconds))
            return nil
        }
        let result = await group.next() ?? nil
        group.cancelAll()
        return result
    }
}
