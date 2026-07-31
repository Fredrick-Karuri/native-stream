// File: PlaybackBitrate.swift
//
// Pure bitrate-cap selection extracted from PlayerViewModel.setQuality. No
// dependency on AVPlayer — takes a StreamQuality and returns the
// preferredPeakBitRate value PlayerViewModel should apply.

import Foundation

enum PlaybackBitrate {

    /// Returns the preferredPeakBitRate (bits/sec) for a given quality selection.
    /// 0 means unlimited/unrestricted, matching AVPlayerItem's convention.
    /// Mirrors PlayerViewModel.setQuality's switch exactly, including the
    /// fallback of 0 for any resolution not in the known set.
    static func preferredPeakBitRate(for quality: StreamQuality) -> Double {
        switch quality {
        case .auto:
            return 0
        case .locked(let height):
            switch height {
            case 1080: return 8_000_000
            case 720:  return 4_000_000
            case 480:  return 1_500_000
            default:   return 0
            }
        }
    }
}
