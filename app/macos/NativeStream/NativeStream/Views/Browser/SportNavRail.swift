// SportNavRail.swift — UX-007, UX-008
import SwiftUI

// MARK: - App Destination

enum AppDestination: Hashable {
    case now
    case sport(SportCategory)
    case favourites
    case schedule
    case allChannels
    case help
    case settings
}

// MARK: - Sport Category

enum SportCategory: String, CaseIterable, Hashable {
    case football   = "football"
    case rugby      = "rugby"
    case tennis     = "tennis"
    case basketball = "basketball"
    case cricket    = "cricket"
    case golf       = "golf"

    var icon: String {
        switch self {
        case .football:   return "soccerball"
        case .rugby:      return "oval.fill"
        case .tennis:     return "tennisball.fill"
        case .basketball: return "basketball.fill"
        case .cricket:    return "cricket.ball"
        case .golf:       return "figure.golf"
        }
    }

    var label: String { rawValue.capitalized }

    var epgKeywords: [String] {
        switch self {
        case .football:   return ["football","soccer","premier","liga","serie","bundesliga","ligue","champions","europa","nwsl","mls"]
        case .rugby:      return ["rugby","premiership rugby","six nations","pro14"]
        case .tennis:     return ["tennis","atp","wta","wimbledon"]
        case .basketball: return ["basketball","nba","wnba","euroleague"]
        case .cricket:    return ["cricket","ipl","test","odi"]
        case .golf:       return ["golf","pga","lpga","masters","open championship","ryder"]
        }
    }
}

// MARK: - Sport Nav Rail

struct SportNavRail: View {
    @Binding var destination: AppDestination

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM

    private var visibleSports: [SportCategory] {
        let active = epgVM.activeSports(in: playlistVM.channels)
        return active.isEmpty ? SportCategory.allCases : active
    }

    var body: some View {
        VStack(spacing: 2) {
            RailIcon(icon: "play.fill", label: "Now", isActive: destination == .now) {
                destination = .now
            }

            railDivider

            ForEach(visibleSports, id: \.self) { sport in
                RailIcon(icon: sport.icon, label: sport.label, isActive: destination == .sport(sport)) {
                    destination = .sport(sport)
                }
            }

            Spacer()

            railDivider

            RailIcon(icon: "star", label: "Favourites", isActive: destination == .favourites) {
                destination = .favourites
            }
            RailIcon(icon: "calendar", label: "Schedule", isActive: destination == .schedule) {
                destination = .schedule
            }
            RailIcon(icon: "square.grid.2x2", label: "All Channels", isActive: destination == .allChannels) {
                destination = .allChannels
            }

            railDivider

            RailIcon(icon: "questionmark.circle", label: "Help", isActive: destination == .help) {
                destination = .help
            }
            RailIcon(icon: "gearshape", label: "Settings", isActive: destination == .settings) {
                destination = .settings
            }
        }
        .padding(.vertical, NS.Spacing.md)
        .frame(width: NS.Rail.width)
        .background(NS.surface)
        .overlay(alignment: .trailing) {
            Rectangle().fill(NS.border).frame(width: 0.5)
        }
    }

    private var railDivider: some View {
        Rectangle()
            .fill(NS.border)
            .frame(width: NS.Rail.iconSize * 0.63, height: 0.5)   
            .padding(.vertical, NS.Spacing.xs)
    }
}

// MARK: - Rail Icon

struct RailIcon: View {
    let icon: String
    let label: String
    let isActive: Bool
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            RailIconLabel(icon: icon, isActive: isActive, isHovered: isHovered)
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
        .help(label)
    }
}

struct RailIconLabel: View {
    let icon: String
    let isActive: Bool
    var isHovered: Bool = false

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: 15 * NS.scale, weight: .medium))
            .foregroundStyle(isActive ? NS.accent2 : isHovered ? NS.text2 : NS.text3)
            .frame(width: NS.Rail.iconSize, height: NS.Rail.iconSize)
            .background(isActive ? NS.accentGlow : isHovered ? NS.surface2 : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.lg)
                    .stroke(isActive ? NS.accentBorder : Color.clear, lineWidth: 0.5)
            )
    }
}
