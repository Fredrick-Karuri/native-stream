// ChannelSidebarView.swift — NS-052
// Channel list with search, group collapsing, logos, now/next EPG badges.

import SwiftUI

struct ChannelSidebarView: View {

    @Environment(PlaylistViewModel.self)  private var playlistVM
    @Environment(EPGViewModel.self)       private var epgVM
    @Environment(SettingsStore.self)      private var settings
    @Binding var selectedChannel: Channel?
    @Binding var searchText: String

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                TextField("Search channels…", text: $searchText)
                    .textFieldStyle(.plain)
                if !searchText.isEmpty {
                    Button { searchText = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(.background.secondary)

            Divider()

            if playlistVM.isLoading {
                VStack(spacing: 10) {
                    ProgressView()
                    Text("Loading channels…")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if filteredChannels.isEmpty && !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else if playlistVM.channels.isEmpty {
                emptyState
            } else {
                channelList
            }
        }
        .toolbar {
            ToolbarItem {
                Button {
                    Task { await playlistVM.loadAll() }
                } label: {
                    Label("Refresh", systemImage: "arrow.clockwise")
                }
                .disabled(playlistVM.isLoading)
            }
        }
        .navigationTitle("NativeStream")
    }

    // MARK: - Channel list

    private var channelList: some View {
        List(selection: $selectedChannel) {
            // Favourites group
            let favs = favouriteChannels
            if !favs.isEmpty {
                Section("Favourites") {
                    ForEach(favs) { channel in
                        ChannelRow(channel: channel, isSelected: selectedChannel?.id == channel.id)
                            .tag(channel)
                    }
                }
            }

            // Regular groups
            ForEach(visibleGroupNames, id: \.self) { group in
                Section(group) {
                    ForEach(filteredChannels.filter { $0.groupTitle == group }) { channel in
                        ChannelRow(channel: channel, isSelected: selectedChannel?.id == channel.id)
                            .tag(channel)
                    }
                }
            }
        }
        .listStyle(.sidebar)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "play.tv")
                .font(.system(size: 40))
                .foregroundStyle(.secondary)
            Text("No channels loaded")
                .font(.headline)
            Text("Add a playlist source in Settings.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Computed

    private var filteredChannels: [Channel] {
        guard !searchText.isEmpty else { return playlistVM.channels }
        return playlistVM.channels.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.groupTitle.localizedCaseInsensitiveContains(searchText)
        }
    }

    private var favouriteChannels: [Channel] {
        playlistVM.channels.filter { settings.isFavourite($0) }
    }

    private var visibleGroupNames: [String] {
        let groups = Set(filteredChannels.map(\.groupTitle))
        return playlistVM.sortedGroupNames.filter { groups.contains($0) }
    }
}