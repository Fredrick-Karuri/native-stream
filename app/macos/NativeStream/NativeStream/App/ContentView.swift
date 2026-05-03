// ContentView.swift — NS-051
// Root NavigationSplitView: sidebar + player detail.

import SwiftUI

struct ContentView: View {

    @Environment(PlaylistViewModel.self)  private var playlistVM
    @Environment(EPGViewModel.self)       private var epgVM
    @Environment(PlayerViewModel.self)   private var playerVM
    @Environment(SettingsStore.self)      private var settings

    @State private var selectedChannel: Channel? = nil
    @State private var searchText: String = ""
    @State private var columnVisibility = NavigationSplitViewVisibility.all

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            ChannelSidebarView(
                selectedChannel: $selectedChannel,
                searchText: $searchText
            )
            .navigationSplitViewColumnWidth(min: 240, ideal: 280, max: 340)
        } detail: {
            PlayerView(channel: selectedChannel)
        }
        .navigationSplitViewStyle(.balanced)
        .task {
            epgVM.epgURL = settings.epgURL
            await playlistVM.loadAll()
            await epgVM.load()
            playlistVM.scheduleAutoRefresh()
        }
        .onChange(of: selectedChannel) { _, channel in
            guard let channel else { return }
            playerVM.bufferPreset = settings.bufferPreset
            playerVM.epgViewModel = epgVM
            playerVM.play(channel: channel)
        }
        .onChange(of: settings.serverURLString) { _, _ in
            Task { await playlistVM.loadAll() }
        }
    }
}

#Preview {
    ContentView()
}
