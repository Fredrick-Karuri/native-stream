import Foundation

enum RefreshInterval: String, Codable, CaseIterable, Sendable {
    case manual    = "manual"
    case oneHour   = "oneHour"
    case sixHours  = "sixHours"
    case daily     = "daily"

    var seconds: TimeInterval {
        switch self {
        case .manual:    return 0
        case .oneHour:   return 3_600
        case .sixHours:  return 21_600
        case .daily:     return 86_400
        }
    }

    var displayName: String {
        switch self {
        case .manual:   return "Manual"
        case .oneHour:  return "Every Hour"
        case .sixHours: return "Every 6 Hours"
        case .daily:    return "Daily"
        }
    }
}