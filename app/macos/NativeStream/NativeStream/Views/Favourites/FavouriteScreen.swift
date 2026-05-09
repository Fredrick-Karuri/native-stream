// FavouritesScreen.swift — UX-016
// Starred channels only. Live first, then up next.

import SwiftUI

struct FavouritesScreen: View {

    @Environment(PlaylistViewModel.self)  private var playlistVM
    @Environment(EPGViewModel.self)       private var epgVM
    @Environment(FavouritesManager.self)  private var favourites
    @Environment(PlayerViewModel.self)    private var playerVM

    let onSelectChannel: (Channel) -> Void

    private var starred: [Channel] { favourites.favourites(from: playlistVM.channels) }

    private var liveNow: [(channel: Channel, programme: Programme)] {
        starred.compactMap { ch in
            guard let prog = epgVM.currentProgramme(for: ch) else { return nil }
            return (ch, prog)
        }
    }

    private var upNext: [(channel: Channel, programme: Programme)] {
        starred.compactMap { ch in
            guard epgVM.currentProgramme(for: ch) == nil,
                  let next = epgVM.nextProgramme(for: ch) else { return nil }
            return (ch, next)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)

            if starred.isEmpty {
                emptyView
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                        if !liveNow.isEmpty { liveSection }
                        if !upNext.isEmpty  { upNextSection }
                    }
                    .padding(NS.Spacing.xl)
                    .padding(.bottom, 80)
                }
            }
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack {
            Text("Favourites")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)
            Spacer()
            Text("\(starred.count) channels")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    // MARK: - Sections

    private var liveSection: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                NSPulseDot()
                NSGroupHeader(title: "Live now", count: liveNow.count)
            }
            VStack(spacing: NS.Spacing.sm) {
                ForEach(liveNow, id: \.channel.id) { item in
                    FavouriteRow(
                        channel: item.channel,
                        programme: item.programme,
                        isPlaying: playerVM.currentChannel?.id == item.channel.id,
                        isUpcoming: false
                    ) { onSelectChannel(item.channel) }
                }
            }
        }
    }

    private var upNextSection: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: "clock")
                    .font(.system(size: 11))
                    .foregroundStyle(NS.text3)
                NSGroupHeader(title: "Up next", count: upNext.count)
            }
            VStack(spacing: NS.Spacing.sm) {
                ForEach(upNext, id: \.channel.id) { item in
                    FavouriteRow(
                        channel: item.channel,
                        programme: item.programme,
                        isPlaying: false,
                        isUpcoming: true
                    ) { onSelectChannel(item.channel) }
                }
            }
        }
    }

    // MARK: - Empty

    private var emptyView: some View {
        VStack(spacing: NS.Spacing.md) {
            Image(systemName: "star")
                .font(.system(size: 32))
                .foregroundStyle(NS.text3.opacity(0.4))
            Text("No favourites yet")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)
            Text("Tap the star on any channel to add it here.")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Favourite Row (UX-017)

struct FavouriteRow: View {

    @Environment(FavouritesManager.self) private var favourites

    let channel: Channel
    let programme: Programme
    let isPlaying: Bool
    let isUpcoming: Bool
    let onTap: () -> Void

    private var isLive: Bool { programme.isNow }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: NS.Spacing.md) {

                ChannelLogoSquare(channel: channel, size: 36)

                // Body
                VStack(alignment: .leading, spacing: 3) {
                    Text(channel.name)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)

                    if isLive, let teams = teamsFromTitle {
                        // Match teams row
                        HStack(spacing: NS.Spacing.xs) {
                            Text(teams.home)
                                .font(NS.Font.monoSm)
                                .foregroundStyle(NS.text2)
                            Text("vs")
                                .font(NS.Font.monoSm)
                                .foregroundStyle(NS.text3)
                            Text(teams.away)
                                .font(NS.Font.monoSm)
                                .foregroundStyle(NS.text2)
                        }
                    } else {
                        Text(programme.title)
                            .font(NS.Font.caption)
                            .foregroundStyle(NS.text3)
                            .lineLimit(1)
                    }

                    if isLive {
                        NSProgressBar(value: programme.progress, height: 2, glow: false)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Right badge
                if isPlaying {
                    playingBadge
                } else if isLive {
                    NSLiveBadge(isLive: true)
                } else {
                    Text(programme.startTimeString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.accent)
                }

                // Star
                Button {
                    favourites.toggle(channel)
                } label: {
                    Image(systemName: "star.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(NS.amber)
                }
                .buttonStyle(.plain)
            }
            .padding(NS.Spacing.md)
        }
        .buttonStyle(.plain)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(rowBorderColour, lineWidth: 0.5)
        )
    }

    private var playingBadge: some View {
        HStack(spacing: 3) {
            Image(systemName: "play.fill").font(.system(size: 7))
            Text("NOW").font(NS.Font.monoSm).fontWeight(.bold)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, NS.Spacing.sm)
        .padding(.vertical, 3)
        .background(NS.accentGlow)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
    }

    private var rowBorderColour: Color {
        isPlaying ? NS.accentBorder :
        isLive    ? Color(hex: "ef4444").opacity(0.157) :
                    NS.border
    }

    private var teamsFromTitle: (home: String, away: String)? {
        let parts = programme.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return nil }
        let home = parts[0].trimmingCharacters(in: .whitespaces)
        let away = parts[1]
            .components(separatedBy: " — ")[0]
            .trimmingCharacters(in: .whitespaces)
        return (home, away)
    }
}
