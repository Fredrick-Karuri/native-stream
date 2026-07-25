import Foundation

// MARK: - Stream quality

enum StreamQuality: Equatable, Sendable {
    case auto
    case locked(height: Int)

    var displayName: String {
        switch self {
        case .auto:              return "Auto"
        case .locked(let h):    return "\(h)p"
        }
    }

    static let presets: [StreamQuality] = [
        .auto, .locked(height: 1080), .locked(height: 720), .locked(height: 480)
    ]
}