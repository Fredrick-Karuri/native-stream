/// Features/Navigation/SportNavRail.swift
///
/// Vertical nav rail with three zones:
/// - Top (fixed):   Now
/// - Middle (scrollable): Sport categories — never clips in small windows
/// - Bottom (fixed): Favourites, Schedule, All Channels, Help, Settings

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
        case .football:   return ["football", "soccer", "premier league", "bundesliga", "ligue 1", "champions league", "europa league", "nwsl", "mls"]
        case .rugby:      return ["rugby", "six nations", "pro14"]
        case .tennis:     return ["tennis", "atp tour", "wta tour", "wimbledon"]
        case .basketball: return ["nba", "wnba", "euroleague"]
        case .cricket:    return ["cricket", "ipl cricket", "test match", "odi"]
        case .golf:       return ["golf", "pga tour live", "lpga", "ryder cup", "open championship"]
        }
    }
}

// MARK: - Sport Nav Rail

struct SportNavRail: View {
    @Binding var destination: AppDestination

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM

    private var visibleSports: [SportCategory] {
        if epgVM.isLoading { return SportCategory.allCases }
        let active = epgVM.activeSports(in: playlistVM.channels)
        return active.isEmpty ? SportCategory.allCases : active
    }

    var body: some View {
        VStack(spacing: 0) {

            // ── Top: fixed primary nav ────────────────────────────────────────
            VStack(spacing: NS.Rail.itemSpacing) {
                RailIcon(icon: "play.fill", label: "Now", isActive: destination == .now) {
                    destination = .now
                }
                railDivider
            }
            .padding(.top, NS.Spacing.md)

            // ── Middle: scrollable sport icons ────────────────────────────────
            // FX-013: dim while EPG is loading
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: NS.Rail.itemSpacing) {
                    ForEach(visibleSports, id: \.self) { sport in
                        RailIcon(
                            icon: sport.icon,
                            label: sport.label,
                            isActive: destination == .sport(sport)
                        ) {
                            destination = .sport(sport)
                        }
                        .opacity(epgVM.isLoading ? 0.4 : 1.0)
                    }
                }
                .animation(.easeOut(duration: 0.3), value: epgVM.isLoading)
                .padding(.vertical, NS.Spacing.xs)
            }
            
            Spacer()

            // ── Bottom: fixed utility nav ─────────────────────────────────────
            VStack(spacing: NS.Rail.itemSpacing) {
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
            .padding(.bottom, NS.Spacing.md)
        }
        .frame(width: NS.Rail.width)
        .background(NS.surface)
        .overlay(alignment: .trailing) {
            Rectangle().fill(NS.border).frame(width: 0.5)
        }
    }

    private var railDivider: some View {
        Rectangle()
            .fill(NS.border)
            .frame(width: NS.Rail.dividerWidth, height: NS.Rail.dividerHeight)
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
            RailIconLabel(icon: icon, label: label, isActive: isActive, isHovered: isHovered)
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

// MARK: - Rail Icon Label

struct RailIconLabel: View {
    let icon: String
    let label: String
    let isActive: Bool
    var isHovered: Bool = false

    var body: some View {
        VStack(spacing: NS.Rail.labelSpacing) {
            Image(systemName: icon)
                .font(.system(size: NS.Rail.iconFontSize, weight: .medium))
                .foregroundStyle(isActive ? NS.accent2 : isHovered ? NS.text2 : NS.text3)
                .frame(width: NS.Rail.iconSize, height: NS.Rail.iconSize)
                .background(isActive ? NS.accentGlow : isHovered ? NS.surface2 : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
                .overlay(
                    RoundedRectangle(cornerRadius: NS.Radius.lg)
                        .stroke(isActive ? NS.accentBorder : Color.clear, lineWidth: 0.5)
                )

            if isHovered {
                Text(label)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(isActive ? NS.accent2 : NS.text3)
                    .lineLimit(1)
                    .transition(.opacity.combined(with: .scale(scale: 0.9)))
            }
        }
        .animation(.easeOut(duration: 0.1), value: isHovered)
    }
}
