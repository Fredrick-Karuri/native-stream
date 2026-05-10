// BrowserScreen.swift — UX-026
// All channels browser. Search + group by groupTitle.
// Sport filtering lives in MatchDayScreen. Now screen lives in NowScreen.

import SwiftUI

struct BrowserScreen: View {

    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(EPGViewModel.self)      private var epgVM

    let onSelectChannel: (Channel) -> Void

    @State private var searchText  = ""
    @State private var gridWidth: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            channelContent
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack {
            Text("All Channels")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)
            Spacer()
            HStack(spacing: NS.Spacing.xs) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(NS.text3)
                TextField("Search channels…", text: $searchText)
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text)
                    .textFieldStyle(.plain)
                    .frame(width: 200)
            }
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: 28)
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))

            Text("\(filtered.count) channels")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
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
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl) {
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

    // MARK: - Empty / loading

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
            Text(searchText.isEmpty
                 ? "Add a playlist source in Settings."
                 : "Try a different search term.")
                .font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Filtering & grouping

    private var filtered: [Channel] {
        guard !searchText.isEmpty else { return playlistVM.channels }
        return playlistVM.channels.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.groupTitle.localizedCaseInsensitiveContains(searchText)
        }
    }

    private struct ChannelSection { let name: String; let channels: [Channel] }

    private var groupedSections: [ChannelSection] {
        let groups = Dictionary(grouping: filtered, by: \.groupTitle)
        return groups.keys.sorted().map { ChannelSection(name: $0, channels: groups[$0]!) }
    }
}
