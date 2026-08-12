// OnboardingConnectionState.swift

import Foundation

enum OnboardingConnectionState: Equatable {
    case idle
    case checking
    case success(channels: Int, healthy: Int, hasEpg: Bool, epgFromPlaylist: Bool)
    case failure(FailureReason)

    var isSuccess: Bool {
        if case .success = self { return true }
        return false
    }

    struct SuccessDetails: Equatable {
        let channels: Int
        let healthy: Int
        let hasEpg: Bool
        let epgFromPlaylist: Bool
    }

    var asSuccess: SuccessDetails? {
        if case .success(let channels, let healthy, let hasEpg, let epgFromPlaylist) = self {
            return SuccessDetails(
                channels: channels,
                healthy: healthy,
                hasEpg: hasEpg,
                epgFromPlaylist: epgFromPlaylist
            )
        }
        return nil
    }
}

enum FailureReason: Equatable {
    case unreachable
    case noPlaylist
    case unknown
}
