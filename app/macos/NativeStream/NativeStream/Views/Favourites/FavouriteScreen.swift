// FavouritesScreen.swift
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
            guard let prog = epgVM.currentProgramme(for: ch),
                  prog.isSportMatch else { return nil }
            return (ch, prog)
        }
    }

    private var onAir: [(channel: Channel, programme: Programme)] {
        starred.compactMap { ch in
            guard let prog = epgVM.currentProgramme(for: ch),
                  !prog.isSportMatch else { return nil }
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
                        if !onAir.isEmpty    { onAirSection }
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
    
    private var onAirSection: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: "tv")
                    .font(.system(size: 11))
                    .foregroundStyle(NS.text3)
                NSGroupHeader(title: "On air", count: onAir.count)
            }
            VStack(spacing: NS.Spacing.sm) {
                ForEach(onAir, id: \.channel.id) { item in
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

