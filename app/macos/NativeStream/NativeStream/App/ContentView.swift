// ContentView.swift — NS-051, NS-160, NS-161
// Root view: gates on server health before showing the main UI.

import SwiftUI

struct ContentView: View {

    @Environment(PlaylistViewModel.self)   private var playlistVM
    @Environment(EPGViewModel.self)        private var epgVM
    @Environment(PlayerViewModel.self)    private var playerVM
    @Environment(SettingsStore.self)       private var settings

    @State private var serverHealth     = ServerHealthViewModel()
    @State private var selectedChannel: Channel? = nil
    @State private var searchText       = ""
    @State private var columnVisibility = NavigationSplitViewVisibility.all

    var body: some View {
        Group {
            switch serverHealth.status {
            case .unreachable:
                ServerUnavailableView {
                    Task {
                        guard let url = settings.serverURL else { return }
                        await serverHealth.check(serverURL: url)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            default:
                mainView
            }
        }
        .task {
            guard let url = settings.serverURL else { return }
            await serverHealth.check(serverURL: url)
            epgVM.epgURL = settings.epgURL
            await playlistVM.loadAll()
            await epgVM.load()
            playlistVM.scheduleAutoRefresh()
            serverHealth.startPolling(serverURL: url)
        }
        .onChange(of: selectedChannel) { _, channel in
            guard let channel else { return }
            playerVM.bufferPreset = settings.bufferPreset
            playerVM.epgViewModel = epgVM
            playerVM.play(channel: channel)
        }
    }

    private var mainView: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            ChannelSidebarView(
                selectedChannel: $selectedChannel,
                searchText: $searchText
            )
            .navigationSplitViewColumnWidth(min: 240, ideal: 280, max: 340)
            .safeAreaInset(edge: .bottom) { serverStatusBar }
        } detail: {
            PlayerView(channel: selectedChannel)
        }
        .navigationSplitViewStyle(.balanced)
    }

    @ViewBuilder
    private var serverStatusBar: some View {
        if case .connected(let total, let healthy) = serverHealth.status {
            HStack(spacing: 6) {
                Circle()
                    .fill(.green)
                    .frame(width: 6, height: 6)
                Text("\(healthy)/\(total) streams healthy")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(.background.secondary)
        }
    }
}

#Preview {
    ContentView()
}
