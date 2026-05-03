import Foundation

// MARK: - Player-level errors

enum PlayerError: Error, LocalizedError, Sendable {
    case streamUnreachable(url: URL)
    case streamTimedOut
    case unsupportedFormat
    case maxRetriesExceeded

    var errorDescription: String? {
        switch self {
        case .streamUnreachable(let url):
            return "Stream unreachable: \(url.host ?? url.absoluteString)"
        case .streamTimedOut:
            return "Stream timed out. The server may be overloaded."
        case .unsupportedFormat:
            return "This stream format is not supported."
        case .maxRetriesExceeded:
            return "Stream unavailable after multiple attempts."
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .streamUnreachable, .maxRetriesExceeded:
            return "The link may have expired. Try refreshing the playlist."
        case .streamTimedOut:
            return "Try again in a moment, or switch to another stream."
        case .unsupportedFormat:
            return "Only HLS (M3U8) streams are supported."
        }
    }
}