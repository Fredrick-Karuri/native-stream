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
        .frame(width: 200)
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
                    .font(.system(size: 13))
                    .foregroundStyle(isActive ? NS.accent2 : NS.text3)
                    .frame(width: 16)
                Text(label)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(isActive ? NS.accent2 : (isHovered ? NS.text : NS.text2))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, NS.Spacing.sm)
            .frame(height: 34)
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

// MARK: - Sources section

struct SourcesSection: View {

    @Environment(PlaylistViewModel.self) private var playlistVM
    @State private var showAddForm = false
    @State private var newLabel    = ""
    @State private var newURL      = ""
    @State private var newInterval: RefreshInterval = .sixHours

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("Playlist Sources")

            ForEach(playlistVM.sources) { source in
                SourceRow(source: source) { playlistVM.removeSource(id: source.id) }
            }

            if showAddForm {
                AddSourceForm(
                    label: $newLabel, url: $newURL, interval: $newInterval,
                    onCancel: { showAddForm = false; resetForm() },
                    onAdd: {
                        guard let url = URL(string: newURL) else { return }
                        playlistVM.addSource(PlaylistSource(
                            label: newLabel.isEmpty ? (URL(string: newURL)?.host ?? "Source") : newLabel,
                            url: url, refreshInterval: newInterval))
                        showAddForm = false; resetForm()
                        Task { await playlistVM.loadAll() }
                    }
                )
            } else {
                AddButton(label: "+ Add Source") { showAddForm = true }
            }

            NSSectionDivider()
            SectionTitle("EPG / TV Guide")
            EPGSourceRow()
        }
    }

    private func resetForm() { newLabel = ""; newURL = ""; newInterval = .sixHours }
}

struct SourceRow: View {
    let source: PlaylistSource
    let onDelete: () -> Void

    private var isStale: Bool {
        guard let last = source.lastFetched else { return true }
        return source.refreshInterval.seconds > 0 &&
               Date().timeIntervalSince(last) > source.refreshInterval.seconds * 2
    }

    var body: some View {
        HStack(spacing: NS.Spacing.md) {
            NSHealthDot(score: isStale ? 0.3 : 1.0)
            VStack(alignment: .leading, spacing: 2) {
                Text(source.label).font(NS.Font.captionMed).foregroundStyle(NS.text)
                Text(source.url.absoluteString).font(NS.Font.monoSm).foregroundStyle(NS.text3).lineLimit(1)
            }
            Spacer()
            Text(isStale ? "Manual · stale" : "↻ \(source.refreshInterval.displayName)")
                .font(NS.Font.monoSm).foregroundStyle(isStale ? NS.amber : NS.text3)
            Button(action: onDelete) {
                Image(systemName: "trash").font(.system(size: 11)).foregroundStyle(NS.text3)
            }
            .buttonStyle(.plain)
        }
        .padding(NS.Spacing.md)
        .background(isStale ? Color(hex: "f59e0b").opacity(0.03) : NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(isStale ? Color(hex: "f59e0b").opacity(0.19) : NS.border, lineWidth: 0.5)
        )
    }
}

struct AddSourceForm: View {
    @Binding var label: String
    @Binding var url: String
    @Binding var interval: RefreshInterval
    let onCancel: () -> Void
    let onAdd: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            NSTextField(placeholder: "Label (e.g. Sports Pack)", text: $label)
            NSTextField(placeholder: "URL (https:// or file://)", text: $url)
            HStack {
                Text("Refresh").font(NS.Font.caption).foregroundStyle(NS.text3)
                Picker("", selection: $interval) {
                    ForEach(RefreshInterval.allCases, id: \.self) { Text($0.displayName).tag($0) }
                }.labelsHidden().frame(width: 160)
                Spacer()
                Button("Cancel", action: onCancel).buttonStyle(.bordered)
                Button("Add", action: onAdd).buttonStyle(.borderedProminent).disabled(url.isEmpty)
            }
        }
        .padding(NS.Spacing.md)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.lg).stroke(NS.border2, lineWidth: 0.5))
    }
}

struct EPGSourceRow: View {
    @Environment(SettingsStore.self) private var settings

    var body: some View {
        HStack(spacing: NS.Spacing.md) {
            NSHealthDot(score: settings.epgURLString.isEmpty ? 0.3 : 1.0)
            VStack(alignment: .leading, spacing: 2) {
                Text("TV Guide (XMLTV)").font(NS.Font.captionMed).foregroundStyle(NS.text)
                Text(settings.epgURLString.isEmpty ? "Not configured" : settings.epgURLString)
                    .font(NS.Font.monoSm).foregroundStyle(NS.text3).lineLimit(1)
            }
            Spacer()
            Text("↻ \(settings.epgRefreshInterval.displayName)").font(NS.Font.monoSm).foregroundStyle(NS.text3)
        }
        .padding(NS.Spacing.md)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.lg).stroke(NS.border, lineWidth: 0.5))
    }
}

// MARK: - Playback section

struct PlaybackSection: View {
    @Environment(SettingsStore.self) private var settings
    @State private var hwDecoding = true

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("Playback")
            SettingsRow(title: "Buffer Preset", subtitle: "Tradeoff between latency and stability") {
                NSSegmentedPicker(
                    options: BufferPreset.allCases.map { (label: $0.displayName, value: $0) },
                    selected: Binding(get: { settings.bufferPreset }, set: { settings.bufferPreset = $0 })
                )
            }
            SettingsRow(title: "Hardware Decoding", subtitle: "Apple Silicon VideoToolbox — always on for M-series") {
                NSToggle(isOn: $hwDecoding).disabled(true).opacity(0.6)
            }
        }
    }
}

// MARK: - TV Guide section

struct TVGuideSection: View {
    @Environment(SettingsStore.self) private var settings

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("TV Guide")
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                Text("EPG URL").font(NS.Font.caption).foregroundStyle(NS.text3)
                NSTextField(
                    placeholder: "https://localhost:8888/epg.xml",
                    text: Binding(get: { settings.epgURLString }, set: { settings.epgURLString = $0 })
                )
                Text("Recommended: your server's /epg.xml or https://iptv-org.github.io/epg/")
                    .font(NS.Font.monoSm).foregroundStyle(NS.text3)
            }
            SettingsRow(title: "Refresh Interval", subtitle: "") {
                Picker("", selection: Binding(
                    get: { settings.epgRefreshInterval },
                    set: { settings.epgRefreshInterval = $0 }
                )) {
                    ForEach(RefreshInterval.allCases, id: \.self) { Text($0.displayName).tag($0) }
                }.labelsHidden().frame(width: 160)
            }
        }
    }
}

// MARK: - Server section

struct ServerSection: View {
    @Environment(SettingsStore.self) private var settings

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("StreamServer")
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                Text("Server URL").font(NS.Font.caption).foregroundStyle(NS.text3)
                NSTextField(
                    placeholder: "http://localhost:8888",
                    text: Binding(get: { settings.serverURLString }, set: { settings.serverURLString = $0 })
                )
                Text("Default: http://localhost:8888 — change only if running the server remotely.")
                    .font(NS.Font.monoSm).foregroundStyle(NS.text3)
            }
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                Text("Quick Start").font(NS.Font.caption).foregroundStyle(NS.text3)
                NSCodeBlock(code: "make run-server")
                NSCodeBlock(code: "make install-service")
            }
        }
    }
}

// MARK: - Proxy section

struct ProxySection: View {
    @State private var proxyEnabled = false
    @State private var referer      = ""
    @State private var userAgent    = ""

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("HLS Proxy")
            SettingsRow(title: "Enable Proxy",
                        subtitle: "Injects Referer/User-Agent headers. Enable only if streams require it.") {
                NSToggle(isOn: $proxyEnabled)
            }
            if proxyEnabled {
                VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                    Text("Referer").font(NS.Font.caption).foregroundStyle(NS.text3)
                    NSTextField(placeholder: "https://example.com", text: $referer)
                    Text("User-Agent").font(NS.Font.caption).foregroundStyle(NS.text3).padding(.top, NS.Spacing.xs)
                    NSTextField(placeholder: "Mozilla/5.0 ...", text: $userAgent)
                }
            }
        }
    }
}

// MARK: - Discovery section

struct DiscoverySection: View {
    @State private var enabled = false

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("Auto-Discovery")
            SettingsRow(title: "Enable Discovery",
                        subtitle: "Server crawls configured sources for stream links automatically.") {
                NSToggle(isOn: $enabled)
            }
            if enabled {
                VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                    Text("Configure in ~/.config/nativestream/config.yaml")
                        .font(NS.Font.caption).foregroundStyle(NS.text3)
                    NSCodeBlock(code: "discovery_enabled: true")
                }
            } else {
                Text("When enabled, StreamServer automatically discovers and validates stream links. Dead links are replaced without manual intervention.")
                    .font(NS.Font.caption).foregroundStyle(NS.text3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - Shared helpers

struct SectionTitle: View {
    let title: String
    init(_ title: String) { self.title = title }
    var body: some View {
        Text(title.uppercased()).font(NS.Font.label).kerning(1.0).foregroundStyle(NS.text3)
    }
}

struct SettingsRow<Control: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var control: Control

    var body: some View {
        HStack(alignment: .center, spacing: NS.Spacing.lg) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(NS.Font.captionMed).foregroundStyle(NS.text)
                if !subtitle.isEmpty {
                    Text(subtitle).font(NS.Font.monoSm).foregroundStyle(NS.text3)
                }
            }
            Spacer()
            control
        }
        .padding(NS.Spacing.md)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.lg).stroke(NS.border, lineWidth: 0.5))
    }
}

struct AddButton: View {
    let label: String
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(NS.Font.caption)
                .foregroundStyle(isHovered ? NS.accent2 : NS.text3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, NS.Spacing.md)
                .frame(height: 40)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
                .overlay(
                    RoundedRectangle(cornerRadius: NS.Radius.lg)
                        .stroke(isHovered ? NS.accent : NS.border2, style: StrokeStyle(dash: [4]))
                )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
        .animation(.easeOut(duration: 0.1), value: isHovered)
    }
}

struct NSTextField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        TextField(placeholder, text: $text)
            .font(NS.Font.body)
            .foregroundStyle(NS.text)
            .textFieldStyle(.plain)
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: 34)
            .background(NS.bg)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))
    }
}
