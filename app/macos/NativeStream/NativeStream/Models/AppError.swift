// NS-014: AppError + PlayerError
// Typed, user-readable errors. No raw Error objects escape to the UI.

import Foundation

// MARK: - App-level errors

enum AppError: Error, LocalizedError, Sendable {
    case networkUnavailable
    case playlistFetchFailed(url: URL, underlying: Error)
    case playlistParseError(line: Int, reason: String)
    case epgFetchFailed(underlying: Error)
    case epgParseError(reason: String)

    var errorDescription: String? {
        switch self {
        case .networkUnavailable:
            return "No network connection. Please check your internet and try again."
        case .playlistFetchFailed(let url, let err):
            return "Failed to fetch playlist from \(url.host ?? url.absoluteString): \(err.localizedDescription)"
        case .playlistParseError(let line, let reason):
            return "Playlist parse error at line \(line): \(reason)"
        case .epgFetchFailed(let err):
            return "Failed to load TV guide: \(err.localizedDescription)"
        case .epgParseError(let reason):
            return "TV guide data is invalid: \(reason)"
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .networkUnavailable:
            return "Check Wi-Fi or Ethernet connection."
        case .playlistFetchFailed:
            return "Check the playlist URL in Settings and try refreshing."
        case .playlistParseError:
            return "The M3U file may be malformed. Check the source."
        case .epgFetchFailed:
            return "Check the EPG URL in Settings."
        case .epgParseError:
            return "The EPG source may have changed format. Try a different EPG URL."
        }
    }
}
