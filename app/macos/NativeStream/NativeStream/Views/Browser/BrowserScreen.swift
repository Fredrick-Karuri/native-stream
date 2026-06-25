/// /Browser/BrowserScreen.swift
///
/// All Channels browser. Thin coordinator — composes BrowserTopBar,
/// group chips, and channel content. All state lives in BrowserViewModel
/// which is lifted to AppShell so it survives destination switches.

import SwiftUI

struct BrowserScreen: View {

    @Environment(PlaylistViewModel.self)    private var playlistVM
    @Environment(EPGViewModel.self)         private var epgVM
    @Environment(ChannelManagerViewModel.self) private var channelManager
    @Environment(BrowserViewModel.self)     private var browserVM
    @Environment(FavouritesManager.self) private var favourites

    let onSelectChannel: (Channel) -> Void

    @FocusState private var searchFocused: Bool
    @State private var activeSports: [SportCategory] = []
    
    private var displayedSections: [ChannelSection] {
        guard let sport = browserVM.selectedSport else {
            return browserVM.groupedSections
        }
        return browserVM.groupedSections.map { section in
            let filtered = section.channels.filter { channel in
                guard let prog = epgVM.currentProgramme(for: channel)
                              ?? epgVM.nextProgramme(for: channel) else { return false }
                return epgVM.matchesSport(sport, programme: prog, channel: channel)
            }
            return ChannelSection(name: section.name, channels: filtered)
        }.filter { !$0.channels.isEmpty }
    }


    var body: some View {
        VStack(spacing: 0) {
            TopBar(
                searchText:    Bindable(browserVM).searchText,
                searchFocused: $searchFocused,
                channelCount:  browserVM.filteredCount,
                onAddChannel:  { browserVM.showAddChannel = true }
            )
            Divider().overlay(NS.border)

            BrowserGroupChips(
                allGroupNames:      browserVM.allGroupNames,
                subGroupNames:      browserVM.subGroupNames,
                activeSports:       activeSports,
                selectedSource:     browserVM.selectedSource,
                selectedGroup:      browserVM.selectedGroup,
                selectedSubGroup:   browserVM.selectedSubGroup,
                selectedSport:      browserVM.selectedSport,
                showFavouritesOnly: browserVM.showFavouritesOnly,
                onSelectAll:        { browserVM.selectGroup(nil, channels: playlistVM.channels,
                                                            favouriteIDs: favourites.favouriteIDs) },
                onSelectGroup:      { browserVM.selectGroup($0, channels: playlistVM.channels,
                                                            favouriteIDs: favourites.favouriteIDs) },
                onSelectSubGroup:   { browserVM.selectSubGroup($0, channels: playlistVM.channels,
                                                               favouriteIDs: favourites.favouriteIDs) },
                onSelectSport:      { browserVM.selectSport($0, channels: playlistVM.channels,
                                                            favouriteIDs: favourites.favouriteIDs) },
                onToggleFavourites: { browserVM.toggleFavourites(channels: playlistVM.channels,
                                                                 favouriteIDs: favourites.favouriteIDs) }
            )
            Divider().overlay(NS.border)

            BrowserContent(
                sections:        displayedSections,
                isLoading:       playlistVM.isLoading,
                searchText:      browserVM.searchText,
                showSourceBadge: browserVM.selectedSource == nil || browserVM.selectedSource!.isAll,
                sources:         playlistVM.sources,
                onSelectChannel: onSelectChannel
            )        }
        .background(NS.bg)
        .onTapGesture { searchFocused = false }
        .sheet(isPresented: Bindable(browserVM).showAddChannel) {
            AddChannelSheet { newChannel in
                browserVM.showAddChannel = false
                if let newChannel {
                    playlistVM.insert(newChannel)
                }
            }
            .environment(channelManager)
        }
        .task(id: playlistVM.channels.count) {
            browserVM.recomputeSections(channels: playlistVM.channels)
        }
        .onChange(of: browserVM.searchText) {
            browserVM.clearGroupWhenSearching()
            browserVM.recomputeSections(channels: playlistVM.channels)
        }
        .onChange(of: browserVM.selectedGroup) {
            browserVM.recomputeSections(channels: playlistVM.channels)
        }
        .onAppear {
            browserVM.restoreSelection(
                from:     playlistVM.sources,
                channels: playlistVM.channels
            )
        }
        .task(id: playlistVM.channels.count) {
            browserVM.recomputeSections(
                channels:     playlistVM.channels,
                favouriteIDs: favourites.favouriteIDs
            )
        }
        .onChange(of: browserVM.searchText) {
            browserVM.clearGroupWhenSearching(
                channels:     playlistVM.channels,
                favouriteIDs: favourites.favouriteIDs
            )
            browserVM.recomputeSections(
                channels:     playlistVM.channels,
                favouriteIDs: favourites.favouriteIDs
            )
        }
        .onChange(of: browserVM.selectedGroup) {
            browserVM.recomputeSections(
                channels:     playlistVM.channels,
                favouriteIDs: favourites.favouriteIDs
            )
        }
        .task(id: browserVM.selectedGroup) {
            guard let group = browserVM.selectedGroup,
                  group.lowercased().contains("sport") else {
                activeSports = []
                return
            }
            let channels = playlistVM.channels
            activeSports = await Task.detached(priority: .userInitiated) {
                epgVM.activeSports(in: channels)
            }.value
        }
    }
}
