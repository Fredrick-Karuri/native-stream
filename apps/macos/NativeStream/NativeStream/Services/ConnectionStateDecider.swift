// File: ConnectionStateDecider.swift
//
// Pure decision logic extracted from ServerHealthViewModel.checkConnection.
// Given the (already-fetched) health/playlist/epg results, decides which
// OnboardingConnectionState applies.

import Foundation
import SdkGenSwift

enum ConnectionStateDecider {
    
    static func decide(
        health: Stream_V1_HealthResponse?,
        playlist: Data?,
        epg: Data?,
        healthError: Error? = nil,
        playlistError: Error? = nil
    ) -> OnboardingConnectionState {
        guard let health else {
            if let urlError = healthError as? URLError,
               urlError.code == .serverCertificateUntrusted || urlError.code == .secureConnectionFailed {
                return .failure(.certificateInvalid)
            }
            return .failure(.unreachable)
        }
 
        let playlistUnauthorized: Bool = {
            if case .httpError(401, _) = playlistError as? APIError { return true }
            return false
        }()
 
        if playlistUnauthorized {
            return .success(
                channels: Int(health.channels),
                healthy: Int(health.healthy),
                hasEpg: false,
                epgFromPlaylist: false
            )
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
