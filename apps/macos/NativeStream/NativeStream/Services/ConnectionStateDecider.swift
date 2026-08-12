// File: ConnectionStateDecider.swift
//
// Pure decision logic extracted from ServerHealthViewModel.checkConnection.
// Given the (already-fetched) health/playlist/epg results, decides which
// OnboardingConnectionState applies.

import Foundation
import SdkGenSwift

enum ConnectionStateDecider {

    /// Mirrors checkConnection's branching exactly:
    /// 1. No health response → .failure(.unreachable)
    /// 2. Health present but playlist missing/empty → .failure(.noPlaylist)
    /// 3. Both present → .success, with hasEpg true only if epg data is non-empty
    static func decide(health: Stream_V1_HealthResponse?, playlist: Data?, epg: Data?) -> OnboardingConnectionState {
        guard let health else {
            return .failure(.unreachable)
        }
        guard let playlist, !playlist.isEmpty else {
            return .failure(.noPlaylist)
        }
        let hasEpg = epg?.isEmpty == false
        return .success(
            channels: Int(health.channels),
            healthy: Int(health.healthy),
            hasEpg: hasEpg,
            epgFromPlaylist: false
        )
    }
}
