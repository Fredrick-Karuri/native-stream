//
//  BrowserContent.swift
//  NativeStream
//
//
 import SwiftUI

// MARK: - Content

struct BrowserContent: View {

    let sections:        [ChannelSection]
    let isLoading:       Bool
    let searchText:      String
    let onSelectChannel: (Channel) -> Void
    let showSourceBadge: Bool
    let sources:         [PlaylistSource]
    
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
                ChannelCard(
                    channel:         channel,
                    onTap:           { onSelectChannel(channel) },
                    showSourceBadge: showSourceBadge,
                    sources:         sources
                )
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
