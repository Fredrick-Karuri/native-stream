// BrowserScreen.swift — UX-010, UX-011, UX-012, UX-013
// Screen 1: Channel browser with sport nav rail, filter bar, card grid, group sections.

import SwiftUI


// MARK: - Filter model

enum ChannelFilter: String, CaseIterable {
    case all     = "All"
    case live    = "🔴 Live now"
    case uk      = "🇬🇧 UK"
    case us      = "🇺🇸 US"
    case hd      = "1080p"
}

// MARK: - Browser screen

struct BrowserScreen: View {

    @Environment(PlaylistViewModel.self)  private var playlistVM
    @Environment(EPGViewModel.self)       private var epgVM
    @Environment(FavouritesManager.self)  private var favourites

    let onSelectChannel: (Channel) -> Void

    @State private var sport: SportCategory = .favourites
    @State private var filter: ChannelFilter = .all
    @State private var searchText = ""
    @State private var viewMode: ViewMode = .grid
    @State private var gridWidth: CGFloat = 0

    enum ViewMode { case grid, list }

    var body: some View {
        HStack(spacing: 0) {
            SportNavRail(selected: $sport)
            Divider().overlay(NS.border)

            VStack(spacing: 0) {
                FilterBar(
                    searchText: $searchText,
                    filter: $filter,
                    viewMode: $viewMode,
                    channelCount: filtered.count
                )
                Divider().overlay(NS.border)
                channelContent
            }
        }
        .background(NS.bg)
    }

    // MARK: - Content

    @ViewBuilder
    private var channelContent: some View {
        if playlistVM.isLoading {
            loadingView
        } else if filtered.isEmpty {
            emptyView
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl, pinnedViews: []) {
                    ForEach(groupedSections, id: \.name) { section in
                        VStack(alignment: .leading, spacing: NS.Spacing.md) {
                            NSGroupHeader(title: section.name, count: section.channels.count)
                                .padding(.horizontal, NS.Spacing.xl)
                            channelGrid(section.channels)
                        }
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, 80)
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
            ProgressView().scaleEffect(0.8)
            Text("Loading channels…").font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyView: some View {
        VStack(spacing: NS.Spacing.md) {
            Text("📺").font(.system(size: 40))
            Text("No channels found").font(NS.Font.display).foregroundStyle(NS.text)
            Text(searchText.isEmpty ? "Add a playlist source in Settings." : "Try a different search or filter.")
                .font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Filtering & grouping

    private var filtered: [Channel] {
        var channels = playlistVM.channels

        if sport == .favourites {
            channels = favourites.favourites(from: channels)
        } else if sport != .regions {
            let kws = sport.groupKeywords
            channels = channels.filter { ch in
                kws.contains { ch.groupTitle.lowercased().contains($0) }
            }
        }

        if !searchText.isEmpty {
            channels = channels.filter {
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                $0.groupTitle.localizedCaseInsensitiveContains(searchText)
            }
        }

        switch filter {
        case .all: break
        case .live:
            channels = channels.filter { epgVM.currentProgramme(for: $0) != nil }
        case .uk:
            channels = channels.filter { $0.groupTitle.lowercased().contains("uk") || $0.name.lowercased().contains("uk") }
        case .us:
            channels = channels.filter { $0.groupTitle.lowercased().contains("us") || $0.groupTitle.lowercased().contains("usa") }
        case .hd:
            channels = channels.filter { ($0 as AnyObject) is Channel } // placeholder
        }

        return channels
    }

    private struct ChannelSection { let name: String; let channels: [Channel] }

    private var groupedSections: [ChannelSection] {
        if sport == .favourites {
            return [ChannelSection(name: "⭐ Favourites", channels: filtered)]
        }
        let groups = Dictionary(grouping: filtered, by: \.groupTitle)
        return groups.keys.sorted().map { ChannelSection(name: $0, channels: groups[$0]!) }
    }
}

// MARK: - Nav Icon

struct NavIcon: View {
    let icon: String
    let isActive: Bool
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Text(icon)
                .font(.system(size: 16))
                .frame(width: 40, height: 40)
                .background(isActive ? NS.accentGlow : (isHovered ? NS.surface2 : Color.clear))
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
                .overlay(
                    RoundedRectangle(cornerRadius: NS.Radius.lg)
                        .stroke(isActive ? NS.accentBorder : Color.clear, lineWidth: 0.5)
                )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

// MARK: - View Toggle Button

struct ViewToggleBtn: View {
    let icon: String
    let isActive: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 11))
                .foregroundStyle(isActive ? NS.text2 : NS.text3)
                .frame(width: 28, height: 28)
                .background(isActive ? NS.surface3 : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
        }
        .buttonStyle(.plain)
    }
}
