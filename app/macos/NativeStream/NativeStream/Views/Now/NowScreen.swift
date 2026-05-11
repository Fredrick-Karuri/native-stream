// NowScreen.swift — UX-009
// Screen 1: EPG-first home. Shows live matches, live on air, and starting soon.

import SwiftUI

struct NowScreen: View {

    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(EPGViewModel.self)      private var epgVM

    let onSelectChannel: (Channel) -> Void

    // MARK: - Bucketing

    private var allSportKeywords: [String] {
        SportCategory.allCases.flatMap { $0.epgKeywords }
    }

    private func isMatch(_ programme: Programme) -> Bool {
        allSportKeywords.contains { programme.title.lowercased().contains($0) }
    }

    /// Channels with a live programme whose title matches a sport keyword.
    private var liveMatches: [(channel: Channel, programme: Programme)] {
        playlistVM.channels.compactMap { channel in
            guard let prog = epgVM.currentProgramme(for: channel), isMatch(prog) else { return nil }
            return (channel, prog)
        }
    }

    /// Channels live but not a sport match — studio shows, PGA coverage, snooker etc.
    private var liveOnAir: [(channel: Channel, programme: Programme)] {
        playlistVM.channels.compactMap { channel in
            guard let prog = epgVM.currentProgramme(for: channel), !isMatch(prog) else { return nil }
            return (channel, prog)
        }
    }

    /// Channels with nothing live but a next programme starting within 2 hours.
    private var startingSoon: [(channel: Channel, programme: Programme)] {
        let cutoff = Date().addingTimeInterval(2 * 3600)
        return playlistVM.channels.compactMap { channel in
            guard epgVM.currentProgramme(for: channel) == nil,
                  let next = epgVM.nextProgramme(for: channel),
                  next.start <= cutoff else { return nil }
            return (channel, next)
        }
    }

    private var liveCount: Int { liveMatches.count + liveOnAir.count }
    private var soonCount: Int { startingSoon.count }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            scrollContent
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: NS.Spacing.sm) {
            Text("What's on")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)
            Spacer()
            Text("\(liveCount) live · \(soonCount) soon")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    // MARK: - Scroll content

    @ViewBuilder
    private var scrollContent: some View {
        if playlistVM.isLoading {
            loadingView
        } else if liveCount == 0 && soonCount == 0 {
            emptyView
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                    if !liveMatches.isEmpty { matchesSection }
                    if !liveOnAir.isEmpty   { onAirSection }
                    if !startingSoon.isEmpty { soonSection }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, 80)
            }
        }
    }

    // MARK: - Matches live section

    private var matchesSection: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                NSPulseDot()
                NSGroupHeader(title: "Matches live", count: liveMatches.count)
            }

            // Hero card — first match
            if let first = liveMatches.first {
                MatchHeroCard(channel: first.channel, programme: first.programme) {
                    onSelectChannel(first.channel)
                }
            }

            // Small grid — remaining matches
            if liveMatches.count > 1 {
                MatchSmallGrid(
                    items: Array(liveMatches.dropFirst()),
                    onSelectChannel: onSelectChannel
                )
            }
        }
    }

    // MARK: - Live on air section

    private var onAirSection: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: "tv")
                    .font(.system(size: 11))
                    .foregroundStyle(NS.text3)
                NSGroupHeader(title: "Live on air", count: liveOnAir.count)
            }
            VStack(spacing: NS.Spacing.sm) {
                ForEach(liveOnAir, id: \.channel.id) { item in
                    LiveOnAirRow(channel: item.channel, programme: item.programme) {
                        onSelectChannel(item.channel)
                    }
                }
            }
        }
    }

    // MARK: - Starting soon section

    private var soonSection: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: "clock")
                    .font(.system(size: 11))
                    .foregroundStyle(NS.text3)
                NSGroupHeader(title: "Starting soon", count: startingSoon.count)
            }
            StartingSoonGrid(items: startingSoon, onSelectChannel: onSelectChannel)
        }
    }

    // MARK: - Loading / empty

    private var loadingView: some View {
        VStack(spacing: NS.Spacing.md) {
            ProgressView().scaleEffect(0.8)
            Text("Loading…").font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyView: some View {
        VStack(spacing: NS.Spacing.md) {
            Text("📺").font(.system(size: 36))
            Text("Nothing on right now").font(NS.Font.display).foregroundStyle(NS.text)
            Text("Add a playlist source in Settings or check back later.")
                .font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
