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

    @ViewBuilder
    private func channelGrid(_ channels: [Channel]) -> some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 148, maximum: 200), spacing: 8)],
            spacing: 8
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

// MARK: - Sport Nav Rail (UX-010)

struct SportNavRail: View {
    @Binding var selected: SportCategory

    private let topSports: [SportCategory] = [.favourites, .football, .rugby, .tennis, .basketball, .cricket]

    var body: some View {
        VStack(spacing: 4) {
            ForEach(topSports, id: \.self) { sport in
                NavIcon(icon: sport.rawValue, isActive: selected == sport) {
                    selected = sport
                }
            }
            Divider().frame(width: 32).overlay(NS.border).padding(.vertical, 4)
            NavIcon(icon: SportCategory.regions.rawValue, isActive: selected == .regions) {
                selected = .regions
            }
            Spacer()
        }
        .padding(.vertical, 12)
        .frame(width: 64)
        .background(NS.surface)
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

// MARK: - Filter Bar (UX-011)

struct FilterBar: View {
    @Binding var searchText: String
    @Binding var filter: ChannelFilter
    @Binding var viewMode: BrowserScreen.ViewMode
    let channelCount: Int

    var body: some View {
        HStack(spacing: 10) {
            // Search
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(NS.text3)
                TextField("Search channels…", text: $searchText)
                    .font(NS.Font.body)
                    .foregroundStyle(NS.text)
                    .textFieldStyle(.plain)
                if !searchText.isEmpty {
                    Button { searchText = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 11))
                            .foregroundStyle(NS.text3)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .frame(width: 240, height: 32)
            .background(NS.bg)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(NS.border2))

            // Filter chips
            HStack(spacing: 6) {
                ForEach(ChannelFilter.allCases, id: \.self) { f in
                    NSChip(label: f.rawValue, isActive: filter == f) {
                        filter = f
                    }
                }
            }

            Spacer()

            Text("\(channelCount) channels")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)

            // View toggle
            HStack(spacing: 0) {
                ViewToggleBtn(icon: "square.grid.2x2", isActive: viewMode == .grid) { viewMode = .grid }
                ViewToggleBtn(icon: "list.bullet", isActive: viewMode == .list)     { viewMode = .list }
            }
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: 7))
            .overlay(RoundedRectangle(cornerRadius: 7).stroke(NS.border))
        }
        .padding(.horizontal, NS.Spacing.xl)
        .frame(height: 52)
        .background(NS.surface)
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

// MARK: - Channel Card (UX-012)

struct ChannelCard: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlayerViewModel.self)   private var playerVM
    @Environment(FavouritesManager.self) private var favourites

    let channel: Channel
    let onTap: () -> Void

    @State private var isHovered = false

    private var isLive: Bool    { epgVM.currentProgramme(for: channel) != nil }
    private var isPlaying: Bool { playerVM.currentChannel?.id == channel.id }
    private var programme: Programme? { epgVM.currentProgramme(for: channel) }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                // Logo area
                ZStack(alignment: .topTrailing) {
                    ChannelLogoView(channel: channel)
                        .frame(maxWidth: .infinity)
                        .aspectRatio(16/9, contentMode: .fit)

                    if isLive {
                        Text("LIVE")
                            .font(.system(size: 8, weight: .bold))
                            .kerning(0.6)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(NS.live)
                            .clipShape(RoundedRectangle(cornerRadius: 3))
                            .padding(5)
                    }
                }

                // Name
                Text(channel.name)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text)
                    .lineLimit(1)

                // Programme / EPG
                if let prog = programme {
                    Text(prog.title)
                        .font(.system(size: 10))
                        .foregroundStyle(NS.accent2)
                        .lineLimit(1)
                    NSProgressBar(value: prog.progress, height: 2, glow: false)
                } else if let next = epgVM.nextProgramme(for: channel) {
                    Text(next.title)
                        .font(.system(size: 10))
                        .foregroundStyle(NS.text3)
                        .lineLimit(1)
                }

                // Footer
                HStack {
                    NSQualityBadge(quality: "720p")
                    Spacer()
                    NSHealthDot(score: 0.9)
                    Button {
                        favourites.toggle(channel)
                    } label: {
                        Image(systemName: favourites.isFavourite(channel) ? "star.fill" : "star")
                            .font(.system(size: 11))
                            .foregroundStyle(favourites.isFavourite(channel) ? NS.accent : NS.text3)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(12)
        }
        .buttonStyle(.plain)
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(cardBorder)
        .offset(y: isHovered ? -1 : 0)
        .shadow(color: .black.opacity(isHovered ? 0.31 : 0), radius: isHovered ? 12 : 0, y: 8)
        .animation(.easeOut(duration: 0.12), value: isHovered)
        .onHover { isHovered = $0 }
    }

    @ViewBuilder
    private var cardBackground: some View {
        if isPlaying {
            NS.activeCardGradient
        } else if isLive {
            NS.liveCardGradient
        } else if isHovered {
            NS.surface3
        } else {
            NS.surface2
        }
    }

    private var cardBorder: some View {
        RoundedRectangle(cornerRadius: NS.Radius.lg)
            .stroke(
                isPlaying ? NS.accentBorder :
                isLive    ? Color(hex: "ef4444").opacity(0.157) :
                isHovered ? NS.border3 : NS.border
            )
    }
}

// MARK: - Channel Logo

struct ChannelLogoView: View {
    let channel: Channel

    var body: some View {
        Group {
            if let url = channel.logoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFit()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .background(NS.bg)
        .clipShape(RoundedRectangle(cornerRadius: 7))
        .overlay(RoundedRectangle(cornerRadius: 7).stroke(NS.border))
    }

    private var placeholder: some View {
        ZStack {
            NS.bg
            Text(channel.name.prefix(3).uppercased())
                .font(NS.Font.label)
                .foregroundStyle(NS.text3)
        }
    }
}