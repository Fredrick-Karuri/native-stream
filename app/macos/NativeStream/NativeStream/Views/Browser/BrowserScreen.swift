// BrowserScreen.swift — UX-010, UX-011, UX-012, UX-013
// Screen 1: Channel browser with sport nav rail, filter bar, card grid, group sections.

import SwiftUI

// MARK: - Sport category

enum SportCategory: String, CaseIterable {
    case favourites  = "⭐"
    case football    = "🏟"
    case rugby       = "🏉"
    case tennis      = "🎾"
    case basketball  = "🏀"
    case cricket     = "🏏"
    case regions     = "🌍"

    var groupKeywords: [String] {
        switch self {
        case .favourites:  return []
        case .football:    return ["football","soccer","premier","liga","serie","bundesliga","ligue","champions","europa","nwsl","mls"]
        case .rugby:       return ["rugby","premiership rugby","six nations","pro14"]
        case .tennis:      return ["tennis","atp","wta","wimbledon"]
        case .basketball:  return ["basketball","nba","wnba","euroleague"]
        case .cricket:     return ["cricket","ipl","test","odi"]
        case .regions:     return []
        }
    }
}

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
                LazyVStack(alignment: .leading, spacing: 28, pinnedViews: []) {
                    ForEach(groupedSections, id: \.name) { section in
                        VStack(alignment: .leading, spacing: 12) {
                            NSGroupHeader(title: section.name, count: section.channels.count)
                                .padding(.horizontal, NS.Spacing.xl)
                            channelGrid(section.channels)
                        }
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, 80) // mini player clearance
            }
        }
    }
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 5)

    @ViewBuilder
    private func channelGrid(_ channels: [Channel]) -> some View {
        LazyVGrid(
            columns: columns,
            spacing: 12
        ) {
            ForEach(channels) { channel in
                ChannelCard(channel: channel) { onSelectChannel(channel) }
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView().scaleEffect(0.8)
            Text("Loading channels…").font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyView: some View {
        VStack(spacing: 12) {
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

        // Sport filter
        if sport == .favourites {
            channels = favourites.favourites(from: channels)
        } else if sport != .regions {
            let kws = sport.groupKeywords
            channels = channels.filter { ch in
                kws.contains { ch.groupTitle.lowercased().contains($0) }
            }
        }

        // Search
        if !searchText.isEmpty {
            channels = channels.filter {
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                $0.groupTitle.localizedCaseInsensitiveContains(searchText)
            }
        }

        // Quick filter chips
        switch filter {
        case .all: break
        case .live:
            channels = channels.filter { epgVM.currentProgramme(for: $0) != nil }
        case .uk:
            channels = channels.filter { $0.groupTitle.lowercased().contains("uk") || $0.name.lowercased().contains("uk") }
        case .us:
            channels = channels.filter { $0.groupTitle.lowercased().contains("us") || $0.groupTitle.lowercased().contains("usa") }
        case .hd:
            channels = channels.filter { ($0 as AnyObject) is Channel } // placeholder — wire to bitrate when available
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
                .background(
                    isActive ? NS.accentGlow :
                    (isHovered ? NS.surface2 : Color.clear)
                )
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isActive ? NS.accentBorder : Color.clear)
                )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}


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
        }
        .buttonStyle(.plain)
    }
}


