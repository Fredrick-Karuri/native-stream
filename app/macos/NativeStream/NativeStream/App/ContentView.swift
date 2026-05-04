// ContentView.swift — Phase 4: onboarding gate + EPG grid tab + server health.

import SwiftUI

struct ContentView: View {

    @Environment(PlaylistViewModel.self)     private var playlistVM
    @Environment(EPGViewModel.self)          private var epgVM
    @Environment(PlayerViewModel.self)       private var playerVM
    @Environment(SettingsStore.self)         private var settings
    @Environment(FavouritesManager.self)     private var favourites
    @Environment(ServerHealthViewModel.self) private var serverHealth

    @State private var selectedChannel: Channel?
    @State private var searchText = ""
    @State private var columnVisibility = NavigationSplitViewVisibility.all
    @State private var showEPGGrid = false
    @State private var showOnboarding = false
    @State private var menuBar = MenuBarManager()
    private let mediaKeys = MediaKeyHandler()

    var body: some View {
        Group {
            if showOnboarding {
                OnboardingView { showOnboarding = false }
            } else {
                mainView
            }
        }
        .task {
            // Show onboarding on first launch
            if !settings.onboardingComplete { showOnboarding = true; return }

            guard let url = settings.serverURL else { return }
            await serverHealth.check(serverURL: url)
            epgVM.epgURL = settings.epgURL
            await playlistVM.loadAll()
            await epgVM.load()
            playlistVM.scheduleAutoRefresh()
            serverHealth.startPolling(serverURL: url)
            mediaKeys.configure(playerVM: playerVM, playlistVM: playlistVM)
        }
        .onChange(of: selectedChannel) { _, channel in
            guard let channel else { return }
            if channel != nil {
                menuBar.show(playerVM: playerVM, epgVM: epgVM)
            } else {
                menuBar.hide()
            }
            playerVM.bufferPreset = settings.bufferPreset
            playerVM.epgViewModel = epgVM
            playerVM.play(channel: channel)
        }
    }

    // MARK: - Main UI

    private var mainView: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            ChannelSidebarView(selectedChannel: $selectedChannel, searchText: $searchText)
                .navigationSplitViewColumnWidth(min: 240, ideal: 280, max: 340)
                .safeAreaInset(edge: .bottom) { serverStatusBar }
        } detail: {
            if showEPGGrid {
                EPGGridView { channel in
                    selectedChannel = channel
                    showEPGGrid = false
                }
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Hide Guide") { showEPGGrid = false }
                    }
                }
            } else {
                PlayerView(channel: selectedChannel)
                    .toolbar {
                        ToolbarItem(placement: .primaryAction) {
                            Button {
                                showEPGGrid = true
                            } label: {
                                Label("TV Guide", systemImage: "rectangle.grid.2x2")
                            }
                        }
                    }
            }
        }
        .navigationSplitViewStyle(.balanced)
    }

    // MARK: - Status bar

    @ViewBuilder
    private var serverStatusBar: some View {
        switch serverHealth.status {
        case .connected(let total, let healthy):
            HStack(spacing: 6) {
                Circle().fill(.green).frame(width: 6, height: 6)
                Text("\(healthy)/\(total) streams healthy")
                    .font(.caption2).foregroundStyle(.secondary)
                Spacer()
                Button {
                    Task { if let url = settings.serverURL { await serverHealth.check(serverURL: url) } }
                } label: {
                    Image(systemName: "arrow.clockwise").font(.caption2)
                }
                .buttonStyle(.plain).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(.background.secondary)

        case .unreachable:
            HStack(spacing: 6) {
                Circle().fill(.red).frame(width: 6, height: 6)
                Text("Server unreachable")
                    .font(.caption2).foregroundStyle(.secondary)
                Spacer()
                Button("Reconnect") {
                    Task { if let url = settings.serverURL { await serverHealth.check(serverURL: url) } }
                }
                .font(.caption2).buttonStyle(.plain).foregroundStyle(Color.accentColor)
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(.background.secondary)

        case .unknown:
            EmptyView()
        }
    }
}

#Preview {
    ContentView()
}
