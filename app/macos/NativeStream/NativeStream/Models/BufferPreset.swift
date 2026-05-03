import Foundation

// MARK: - Buffer preset

enum BufferPreset: String, Codable, CaseIterable, Sendable {
    case low      = "low"        // 2s  — lowest latency, sports-optimised
    case balanced = "balanced"   // 8s  — default
    case reliable = "reliable"   // 30s — poor connections

    var seconds: TimeInterval {
        switch self {
        case .low:      return 2
        case .balanced: return 8
        case .reliable: return 30
        }
    }

    var displayName: String {
        switch self {
        case .low:      return "Low Latency (2s)"
        case .balanced: return "Balanced (8s)"
        case .reliable: return "Reliable (30s)"
        }
    }
}