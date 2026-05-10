//
//  Sections.swift
//  NativeStream

import SwiftUI

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
