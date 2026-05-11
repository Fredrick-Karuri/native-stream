// SettingsScreen.swift — UX-023
// Settings as a full rail destination. No fixed frame, no sheet.

import SwiftUI

enum SettingsSection: String, CaseIterable {
    case sources    = "Sources"
    case playback   = "Playback"
    case tvGuide    = "TV Guide"
    case server     = "Server"
    case proxy      = "Proxy"
    case discovery  = "Discovery"

    var icon: String {
        switch self {
        case .sources:   return "server.rack"
        case .playback:  return "play.circle"
        case .tvGuide:   return "calendar"
        case .server:    return "cpu"
        case .proxy:     return "lock.shield"
        case .discovery: return "radar"
        }
    }
}

struct SettingsScreen: View {

    @Environment(SettingsStore.self)         private var settings
    @Environment(PlaylistViewModel.self)     private var playlistVM
    @Environment(ServerHealthViewModel.self) private var serverHealth

    @State private var selected: SettingsSection = .sources

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            HStack(spacing: 0) {
                sidebar
                Divider().overlay(NS.border)
                ScrollView {
                    panelContent
                        .padding(NS.Spacing.xxl)
                }
                .background(NS.bg)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack {
            Text("Settings")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)
            Spacer()
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    // MARK: - Sidebar

    private var sidebar: some View {
        VStack(spacing: 1) {
            ForEach(SettingsSection.allCases, id: \.self) { sec in
                SettingsNavItem(icon: sec.icon, label: sec.rawValue, isActive: selected == sec) {
                    selected = sec
                }
            }
            Spacer()
            serverHealthCard
        }
        .padding(NS.Spacing.sm)
        .frame(width: NS.Settings.sidebarWidth)
        .background(NS.surface)
    }

    private var serverHealthCard: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.sm) {
            HStack(spacing: NS.Spacing.sm) {
                NSHealthDot(score: serverHealth.isConnected ? 1.0 : 0.0)
                Text(serverHealth.isConnected ? "Server connected" : "Server unreachable")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text2)
            }
            Text(settings.serverURLString)
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)
                .lineLimit(1)
            if case .connected(let total, let healthy) = serverHealth.status {
                Text("\(healthy)/\(total) streams healthy")
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.text3)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading) 
        .padding(NS.Spacing.md)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))
    }

    // MARK: - Panel routing

    @ViewBuilder
    private var panelContent: some View {
        switch selected {
        case .sources:   SourcesSection()
        case .playback:  PlaybackSection()
        case .tvGuide:   TVGuideSection()
        case .server:    ServerSection()
        case .proxy:     ProxySection()
        case .discovery: DiscoverySection()
        }
    }
}

// MARK: - Nav item

struct SettingsNavItem: View {
    let icon: String
    let label: String
    let isActive: Bool
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: icon)
                    .font(.system(size: 13 * NS.scale))
                    .foregroundStyle(isActive ? NS.accent2 : NS.text3)
                    .frame(width: NS.Settings.navIconSize)
                Text(label)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(isActive ? NS.accent2 : (isHovered ? NS.text : NS.text2))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, NS.Spacing.sm)
            .frame(height: NS.Settings.navItemHeight)
            .background(isActive ? NS.accentGlow : (isHovered ? NS.surface2 : Color.clear))
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.md)
                    .stroke(isActive ? NS.accentBorder : Color.clear, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}


