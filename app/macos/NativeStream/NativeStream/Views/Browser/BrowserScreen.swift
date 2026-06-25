/// /Browser/BrowserScreen.swift

import SwiftUI

struct BrowserScreen: View {

    @Environment(PlaylistViewModel.self)       private var playlistVM
    @Environment(EPGViewModel.self)            private var epgVM
    @Environment(ChannelManagerViewModel.self) private var channelManager
    @Environment(BrowserViewModel.self)        private var browserVM
    @Environment(FavouritesManager.self)       private var favourites

    let onSelectChannel: (Channel) -> Void

    @FocusState private var searchFocused: Bool
    @State private var activeSports: [SportCategory] = []

    // MARK: - Derived

    private var displayedSections: [ChannelSection] {
        guard let sport = browserVM.selectedSport else {
            return browserVM.groupedSections
        }
        return browserVM.groupedSections.compactMap { section in
            let filtered = section.channels.filter { channel in
                guard let prog = epgVM.currentProgramme(for: channel)
                              ?? epgVM.nextProgramme(for: channel) else { return false }
                return epgVM.matchesSport(sport, programme: prog, channel: channel)
            }
            return filtered.isEmpty ? nil : ChannelSection(name: section.name, channels: filtered)
        }
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            groupChips
            Divider().overlay(NS.border)
            content
        }
        .background(NS.bg)
        .onTapGesture { searchFocused = false }
        .sheet(isPresented: Bindable(browserVM).showAddChannel) {
            AddChannelSheet { newChannel in
                browserVM.showAddChannel = false
                if let newChannel { playlistVM.insert(newChannel) }
            }
            .environment(channelManager)
        }
        .onAppear {
            browserVM.restoreSelection(
                from:     playlistVM.sources,
                channels: playlistVM.channels
            )
        }
        .task(id: playlistVM.channels.count) {
            recompute()
        }
        .task(id: browserVM.selectedGroup) {
            recomputeSports()
        }
        .onChange(of: browserVM.searchText) {
            browserVM.clearGroupWhenSearching(
                channels:     playlistVM.channels,
                favouriteIDs: favourites.favouriteIDs
            )
            recompute()
        }
        .onChange(of: browserVM.selectedGroup)  { recompute() }
        .onChange(of: browserVM.selectedSource) { recompute() }
        .onChange(of: favourites.favouriteIDs)  { recompute() }
    }

    // MARK: - Subviews

    private var topBar: some View {
        TopBar(
            searchText:     Bindable(browserVM).searchText,
            searchFocused:  $searchFocused,
            channelCount:   browserVM.filteredCount,
            sources:        playlistVM.sources,
            selectedSource: browserVM.selectedSource,
            onSelectSource: { source in
                browserVM.selectSource(source, channels: playlistVM.channels)
            },
            onAddPlaylist:  { /* navigate to Settings → Sources */ },
            onAddChannel:   { browserVM.showAddChannel = true },
            playlistVM:     playlistVM
        )
    }

    private var groupChips: some View {
        BrowserGroupChips(
            allGroupNames:      browserVM.allGroupNames,
            subGroupNames:      browserVM.subGroupNames,
            activeSports:       activeSports,
            selectedSource:     browserVM.selectedSource,
            selectedGroup:      browserVM.selectedGroup,
            selectedSubGroup:   browserVM.selectedSubGroup,
            selectedSport:      browserVM.selectedSport,
            showFavouritesOnly: browserVM.showFavouritesOnly,
            onSelectAll: {
                browserVM.selectGroup(
                    nil,
                    channels:     playlistVM.channels,
                    favouriteIDs: favourites.favouriteIDs
                )
            },
            onSelectGroup: { group in
                browserVM.selectGroup(
                    group,
                    channels:     playlistVM.channels,
                    favouriteIDs: favourites.favouriteIDs
                )
            },
            onSelectSubGroup: { sub in
                browserVM.selectSubGroup(
                    sub,
                    channels:     playlistVM.channels,
                    favouriteIDs: favourites.favouriteIDs
                )
            },
            onSelectSport: { sport in
                browserVM.selectSport(
                    sport,
                    channels:     playlistVM.channels,
                    favouriteIDs: favourites.favouriteIDs
                )
            },
            onToggleFavourites: {
                browserVM.toggleFavourites(
                    channels:     playlistVM.channels,
                    favouriteIDs: favourites.favouriteIDs
                )
            }
        )
    }

    private var content: some View {
        BrowserContent(
            sections:        displayedSections,
            isLoading:       playlistVM.isLoading,
            searchText:      browserVM.searchText,
            onSelectChannel: onSelectChannel,
            showSourceBadge: browserVM.selectedSource == nil
                             || browserVM.selectedSource!.isAll,
            sources:         playlistVM.sources
        )
    }
    // MARK: - Helpers

    private func recompute() {
        browserVM.recomputeSections(
            channels:     playlistVM.channels,
            favouriteIDs: favourites.favouriteIDs
        )
    }

    private func recomputeSports() {
        guard let group = browserVM.selectedGroup,
              group.lowercased().contains("sport") else {
            activeSports = []
            return
        }
        let channels = playlistVM.channels
        Task.detached(priority: .userInitiated) {
            let sports = epgVM.activeSports(in: channels)
            await MainActor.run { activeSports = sports }
        }
    }
}
