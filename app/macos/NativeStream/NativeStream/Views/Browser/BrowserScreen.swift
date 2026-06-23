/// Features/Browser/BrowserScreen.swift
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

    let onSelectChannel: (Channel) -> Void

    @FocusState private var searchFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            BrowserTopBar(
                searchText:    Bindable(browserVM).searchText,
                searchFocused: $searchFocused,
                channelCount:  browserVM.filteredCount,
                onAddChannel:  { browserVM.showAddChannel = true }
            )
            Divider().overlay(NS.border)

            BrowserGroupChips(
                allGroupNames:  browserVM.allGroupNames,
                selectedGroup:  Bindable(browserVM).selectedGroup
            )
            Divider().overlay(NS.border)

            BrowserContent(
                sections:        browserVM.groupedSections,
                isLoading:       playlistVM.isLoading,
                searchText:      browserVM.searchText,
                onSelectChannel: onSelectChannel
            )
        }
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
    }
}

// MARK: - Group chips

private struct BrowserGroupChips: View {

    let allGroupNames: [String]
    @Binding var selectedGroup: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: NS.Spacing.xs) {
                NSChip(label: "All", isActive: selectedGroup == nil) {
                    selectedGroup = nil
                }
                ForEach(allGroupNames, id: \.self) { group in
                    NSChip(label: group, isActive: selectedGroup == group) {
                        selectedGroup = group
                    }
                }
            }
            .padding(.horizontal, NS.Spacing.xl)
            .padding(.vertical, NS.Spacing.sm)
        }
        .background(NS.surface)
    }
}

// MARK: - Content

private struct BrowserContent: View {

    let sections:        [ChannelSection]
    let isLoading:       Bool
    let searchText:      String
    let onSelectChannel: (Channel) -> Void

    @State private var gridWidth: CGFloat = 700

    var body: some View {
        if isLoading {
            loadingView
        } else if sections.isEmpty {
            emptyView
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                    ForEach(sections, id: \.name) { section in
                        VStack(alignment: .leading, spacing: NS.Spacing.md) {
                            NSGroupHeader(title: section.name, count: section.channels.count)
                                .padding(.horizontal, NS.Spacing.xl)
                            channelGrid(section.channels)
                        }
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, NS.Help.emptyTopPadding)
            }
        }
    }

    @ViewBuilder
    private func channelGrid(_ channels: [Channel]) -> some View {
        let columns = max(1, Int(gridWidth / NS.CardSize.minWidth))
        let grid = Array(repeating: GridItem(.flexible(), spacing: NS.Spacing.sm), count: columns)

        LazyVGrid(columns: grid, spacing: NS.Spacing.sm) {
            ForEach(channels) { channel in
                ChannelCard(channel: channel) { onSelectChannel(channel) }
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { gridWidth = geo.size.width }
                    .onChange(of: geo.size.width) { gridWidth = $0 }
            }
        )
    }

    private var loadingView: some View {
        VStack(spacing: NS.Spacing.md) {
            ProgressView().scaleEffect(NS.Browser.loadingScale)
            Text("Loading channels…").font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyView: some View {
        VStack(spacing: NS.Spacing.md) {
            Text("📺").font(.system(size: NS.Browser.emptyEmojiSize))
            Text("No channels found").font(NS.Font.display).foregroundStyle(NS.text)
            Text(searchText.isEmpty
                 ? "Add a playlist source in Settings."
                 : "Try a different search term.")
                .font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
