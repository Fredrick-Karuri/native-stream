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
    private var liveMatches: [MatchResponse] {
        epgVM.matches.filter { $0.isNow }
    }

    /// Channels with nothing live but a next programme starting within 2 hours.
    private var startingSoon: [MatchResponse] {
        let cutoff = Date().addingTimeInterval(2 * 3600)
        return epgVM.matches.filter { !$0.isNow && $0.kickOff <= cutoff }
    }

    private var liveCount: Int { liveMatches.count }
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
        if let first = liveMatches.first {
            // MatchHeroCard needs a match not a channel — adapt or pass title/competition
            Text("\(first.title) · \(first.competition)")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)
        }
        // remaining matches
        ForEach(liveMatches.dropFirst()) { match in
            Text("\(match.title) · \(match.competition)")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text2)
        }
    }
}

    // MARK: - Starting soon section

private var soonSection: some View {
    VStack(alignment: .leading, spacing: NS.Spacing.md) {
        HStack(spacing: NS.Spacing.sm) {
            Image(systemName: "clock").font(.system(size: 11)).foregroundStyle(NS.text3)
            NSGroupHeader(title: "Starting soon", count: startingSoon.count)
        }
        ForEach(startingSoon) { match in
            HStack {
                Text(match.title).font(NS.Font.caption).foregroundStyle(NS.text)
                Spacer()
                Text(match.kickOff, style: .time).font(NS.Font.monoSm).foregroundStyle(NS.accent)
            }
        }
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
