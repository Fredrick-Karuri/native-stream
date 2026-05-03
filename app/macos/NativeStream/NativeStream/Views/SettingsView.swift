// SettingsView.swift — NS-055
// App settings: playlist sources, buffer preset, EPG URL, server URL.

import SwiftUI

struct SettingsView: View {

    @Environment(SettingsStore.self)     private var settings
    @Environment(PlaylistViewModel.self) private var playlistVM

    @State private var showAddSource   = false
    @State private var newSourceLabel  = ""
    @State private var newSourceURL    = ""
    @State private var newRefreshInterval: RefreshInterval = .sixHours

    var body: some View {
        TabView {
            sourcesTab.tabItem { Label("Sources", systemImage: "list.bullet") }
            playbackTab.tabItem { Label("Playback", systemImage: "play.circle") }
            epgTab.tabItem { Label("TV Guide", systemImage: "tv") }
            serverTab.tabItem { Label("Server", systemImage: "server.rack") }
        }
        .frame(width: 480, height: 340)
    }

    // MARK: - Tabs

    private var sourcesTab: some View {
        Form {
            Section("Playlist Sources") {
                ForEach(playlistVM.sources) { source in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(source.label).font(.headline)
                            Text(source.url.absoluteString)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Text(source.refreshInterval.displayName)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Button(role: .destructive) {
                            playlistVM.removeSource(id: source.id)
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.red)
                    }
                }

                if showAddSource {
                    VStack(alignment: .leading, spacing: 8) {
                        TextField("Label (e.g. Sports Pack)", text: $newSourceLabel)
                        TextField("URL (https:// or file://)", text: $newSourceURL)
                        Picker("Refresh", selection: $newRefreshInterval) {
                            ForEach(RefreshInterval.allCases, id: \.self) { interval in
                                Text(interval.displayName).tag(interval)
                            }
                        }
                        .pickerStyle(.menu)
                        HStack {
                            Button("Cancel") {
                                showAddSource = false
                                newSourceLabel = ""
                                newSourceURL = ""
                            }
                            Spacer()
                            Button("Add") { addSource() }
                                .buttonStyle(.borderedProminent)
                                .disabled(newSourceURL.isEmpty)
                        }
                    }
                    .padding(8)
                    .background(.background.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                Button { showAddSource.toggle() } label: {
                    Label("Add Source", systemImage: "plus")
                }
            }
        }
        .formStyle(.grouped)
        .padding()
    }

    private var playbackTab: some View {
        Form {
            Section("Buffer Size") {
                Picker("Buffer Preset", selection: Binding(
                    get: { settings.bufferPreset },
                    set: { settings.bufferPreset = $0 }
                )) {
                    ForEach(BufferPreset.allCases, id: \.self) { preset in
                        Text(preset.displayName).tag(preset)
                    }
                }
                .pickerStyle(.radioGroup)
                Text("Low Latency is best for sports. Reliable helps on slow connections.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding()
    }

    private var epgTab: some View {
        Form {
            Section("TV Guide (EPG)") {
                TextField("EPG URL", text: Binding(
                    get: { settings.epgURLString },
                    set: { settings.epgURLString = $0 }
                ))
                .textFieldStyle(.roundedBorder)

                Picker("Refresh", selection: Binding(
                    get: { settings.epgRefreshInterval },
                    set: { settings.epgRefreshInterval = $0 }
                )) {
                    ForEach(RefreshInterval.allCases, id: \.self) { i in
                        Text(i.displayName).tag(i)
                    }
                }
                Text("Recommended: https://epghub.xyz or your server's /epg.xml endpoint.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding()
    }

    private var serverTab: some View {
        Form {
            Section("StreamServer") {
                TextField("Server URL", text: Binding(
                    get: { settings.serverURLString },
                    set: { settings.serverURLString = $0 }
                ))
                .textFieldStyle(.roundedBorder)
                Text("Default: http://localhost:8888 — set this once the Go server is running.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding()
    }

    // MARK: - Actions

    private func addSource() {
        guard let url = URL(string: newSourceURL) else { return }
        let source = PlaylistSource(
            label: newSourceLabel.isEmpty ? url.host ?? "Source" : newSourceLabel,
            url: url,
            refreshInterval: newRefreshInterval
        )
        playlistVM.addSource(source)
        showAddSource = false
        newSourceLabel = ""
        newSourceURL = ""
        Task { await playlistVM.loadAll() }
    }
}