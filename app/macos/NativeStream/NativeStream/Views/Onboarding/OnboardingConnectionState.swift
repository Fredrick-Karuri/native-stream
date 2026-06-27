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

    var asSuccess: (channels: Int, healthy: Int, hasEpg: Bool, epgFromPlaylist: Bool)? {
        if case .success(let c, let h, let e, let ep) = self { return (c, h, e, ep) }
        return nil
    }
}

enum FailureReason: Equatable {
    case unreachable
    case noPlaylist
    case unknown
}
