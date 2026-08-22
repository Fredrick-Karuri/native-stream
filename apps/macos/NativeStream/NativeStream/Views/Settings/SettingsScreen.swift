// SettingsScreen.swift
// Settings as a full rail destination. No fixed frame, no sheet.

import SwiftUI
import SdkGenSwift

enum SettingsSection: String, CaseIterable {
    case sources    = "Sources"
    case channels   = "Channels"
    case playback   = "Playback"
    case server     = "Server"
    case proxy      = "Proxy"
    case discovery  = "Discovery"

    var icon: String {
        switch self {
        case .sources:   return "server.rack"
        case .channels:  return "tv"
        case .playback:  return "play.circle"
        case .server:    return "cpu"
        case .proxy:     return "lock.shield"
        case .discovery: return "antenna.radiowaves.left.and.right"
        }
    }
}

struct SettingsScreen: View {

    @Environment(SettingsStore.self)         private var settings
    @Environment(SourceViewModel.self)         private var sourceVM
    @Environment(ChannelLoadingViewModel.self) private var channelLoadingVM
    @Environment(ServerHealthViewModel.self) private var serverHealth
    @Environment(ServerDiscoveryService.self) private var discovery
    @Environment(ControlViewModel.self)       private var controlVM
    @Environment(\.scenePhase) private var scenePhase

    @State private var selected: SettingsSection = .sources
    @State private var showResetConfirm = false
    @State private var pendingDiscoveredURL: URL?

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
        .onAppear {
            guard let url = settings.serverURL else { return }
            Task { await serverHealth.check(serverURL: url) }
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active, let url = settings.serverURL else { return }
            Task { await serverHealth.check(serverURL: url) }
        }
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
            discoveredURLPrompt
            if !controlVM.sessions.isEmpty {
                controllerIndicator
            }
            Divider().overlay(NS.border).padding(.vertical, NS.Spacing.xs)
            DestructiveNavItem(
                icon: "arrow.counterclockwise",
                label: "Reset App",
                action: { showResetConfirm = true }
            )

        }
        .padding(NS.Spacing.sm)
        .frame(width: NS.Settings.sidebarWidth)
        .background(NS.surface)
        .confirmationDialog(
            "Reset NativeStream?",
            isPresented: $showResetConfirm,
            titleVisibility: .visible
        ) {
            Button("Reset Everything", role: .destructive) {
                sourceVM.resetAll()
                channelLoadingVM.resetAll()
                settings.resetAll()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Clears all sources, settings, and server config. You'll go through onboarding again.")
        }
    }

    private var serverHealthCard: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.sm) {
            HStack(spacing: NS.Spacing.sm) {
                NSHealthDot(score: serverHealth.isConnected ? 1.0 : 0.0)
                Text(
                    serverHealth.isConnected ? "Server connected"
                    : serverHealth.isAuthFailed ? "Authentication failed"
                    : "Server unreachable"
                )
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text2)
                Spacer()
                if !serverHealth.isConnected {
                    Button(discovery.isScanning ? "Scanning…" : "Scan again") {
                        discovery.scan()
                    }
                    .buttonStyle(.plain)
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.accent)
                    .disabled(discovery.isScanning)
                }
            }
            Text(settings.resolvedServerURL.url)
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)
                .lineLimit(1)
            switch settings.resolvedServerURL.source {
            case .hostedDefault:
                Text("Using hosted default")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
            case .lanDiscovered:
                Text("Discovered on local network")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
            case .manualOverride:
                EmptyView()
            }
            if serverHealth.isAuthFailed {
                Text("Check your API token in Settings")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.red)
            }
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
        .onChange(of: discovery.discoveredURL) { _, url in
            pendingDiscoveredURL = url
        }
    }

    @ViewBuilder
    private var discoveredURLPrompt: some View {
        if let url = pendingDiscoveredURL {
            HStack(spacing: NS.Spacing.sm) {
                Text("Server found at \(url.host ?? url.absoluteString)")
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.text2)
                    .lineLimit(1)
                Spacer()
                Button("Connect") {
                    settings.confirmDiscoveredURL(url)
                    pendingDiscoveredURL = nil
                    Task { await serverHealth.check(serverURL: url) }
                }
                .buttonStyle(.plain)
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.accent)
                Button("Dismiss") { pendingDiscoveredURL = nil }
                    .buttonStyle(.plain)
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
            }
            .padding(NS.Spacing.sm)
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        }
    }

    private var controllerIndicator: some View {
        HStack(spacing: NS.Spacing.sm) {
            Circle()
                .fill(NS.accent)
                .frame(width: 6, height: 6)
            Text(controlVM.sessions.count == 1
                 ? "\(controlVM.sessions[0].name) connected"
                 : "\(controlVM.sessions.count) controllers connected")
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, NS.Spacing.sm)
        .padding(.vertical, NS.Spacing.xs)
    }

    // MARK: - Panel routing

    @ViewBuilder
    private var panelContent: some View {
        switch selected {
        case .sources:   SourcesSection()
        case .channels:  ChannelsSection()
        case .playback:  PlaybackSection()
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

// add after SettingsNavItem

struct DestructiveNavItem: View {
    let icon: String
    let label: String
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: icon)
                    .font(.system(size: 13 * NS.scale))
                    .foregroundStyle(isHovered ? NS.red : NS.text3)
                    .frame(width: NS.Settings.navIconSize)
                Text(label)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(isHovered ? NS.red : NS.text2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, NS.Spacing.sm)
            .frame(height: NS.Settings.navItemHeight)
            .background(isHovered ? NS.red.opacity(0.08) : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .animation(.easeInOut(duration: 0.15), value: isHovered)
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}
