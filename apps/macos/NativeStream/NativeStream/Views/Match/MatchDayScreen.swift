// MatchDayScreen.swift — UX-014
// Sport filter screen. Receives a SportCategory from the rail and shows
// EPG-matched channels grouped into live and upcoming sections.

import SwiftUI

enum MatchCardVariant { case live, featured, ucl, plain }

struct MatchDayScreen: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM

    let sport: SportCategory
    let onSelectChannel: (Channel) -> Void

    @State private var gridWidth: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            MatchDayHero(
                sport: sport,
                totalCount: allMatches.count,
                liveCount: liveMatches.count
            )
            Divider().overlay(NS.border)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                    if !liveMatches.isEmpty {
                        matchSection(title: "Live now", matches: liveMatches, isLiveSection: true)
                    }
                    if !upcomingMatches.isEmpty {
                        matchSection(title: "Up next", matches: upcomingMatches, isLiveSection: false)
                    }
                    if allMatches.isEmpty {
                        emptyView
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, 80)
            }
        }
        .background(NS.bg)
    }

    // MARK: - Section

    @ViewBuilder
    private func matchSection(title: String, matches: [MatchItem], isLiveSection: Bool) -> some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                if isLiveSection { NSPulseDot() }
                NSGroupHeader(title: title, count: matches.count)
            }
            matchGrid(matches)
        }
    }

    @ViewBuilder
    private func matchGrid(_ matches: [MatchItem]) -> some View {
        let columns = max(1, Int(gridWidth / NS.CardSize.minWidth))
        let grid = Array(repeating: GridItem(.flexible(), spacing: NS.Spacing.sm), count: columns)

        LazyVGrid(columns: grid, spacing: NS.Spacing.sm) {
            ForEach(matches) { match in
                MatchCard(match: match) {
                    if let ch = channelFor(match) { onSelectChannel(ch) }
                }
            }
        }
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { gridWidth = geo.size.width }
                    .onChange(of: geo.size.width) { gridWidth = $0 }
            }
        )
    }

    // MARK: - Empty

    private var emptyView: some View {
        VStack(spacing: NS.Spacing.md) {
            Text("📅").font(.system(size: 40))
            Text("No \(sport.label) on right now")
                .font(NS.Font.display).foregroundStyle(NS.text)
            Text("Check back closer to kick-off time.")
                .font(NS.Font.caption).foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 80)
    }

    // MARK: - Data

    struct MatchItem: Identifiable {
        let id = UUID()
        let programme: Programme
        let channelTvgID: String
        let competition: String
        let homeTeam: String
        let awayTeam: String
        var variant: MatchCardVariant = .plain
    }

    private var allMatches: [MatchItem] {
        var items: [MatchItem] = []
        for ch in playlistVM.channels {
            for prog in epgVM.schedule(for: ch, hours: 8) {
                // EPG keyword match first, fall back to groupTitle
                let titleLower = prog.title.lowercased()
                let groupLower = ch.groupTitle.lowercased()
                let matches = sport.epgKeywords.contains {
                    titleLower.contains($0) || groupLower.contains($0)
                }
                guard matches, let parsed = parseMatch(prog, channelID: ch.tvgId) else { continue }
                items.append(parsed)
            }
        }
        return items.sorted { $0.programme.start < $1.programme.start }
    }

    private var liveMatches: [MatchItem]     { allMatches.filter { $0.programme.isNow } }
    private var upcomingMatches: [MatchItem] { allMatches.filter { !$0.programme.isNow && $0.programme.start > Date() } }

    private func parseMatch(_ prog: Programme, channelID: String) -> MatchItem? {
        guard prog.title.contains(" vs ") else { return nil }
        let parts = prog.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return nil }
        let home = parts[0].trimmingCharacters(in: .whitespaces)
        let rest = parts[1].components(separatedBy: " — ")
        let away = rest[0].trimmingCharacters(in: .whitespaces)
        let competition = rest.count > 1 ? rest[1].trimmingCharacters(in: .whitespaces) : ""

        var variant: MatchCardVariant = prog.isNow ? .live : .plain
        if !prog.isNow {
            let c = competition.lowercased()
            if c.contains("champions") || c.contains("ucl") { variant = .ucl }
            else if c.contains("premier") || c.contains("liga") || c.contains("bundesliga") { variant = .featured }
        }

        return MatchItem(
            programme: prog,
            channelTvgID: channelID,
            competition: competition,
            homeTeam: home,
            awayTeam: away,
            variant: variant
        )
    }

    private func channelFor(_ match: MatchItem) -> Channel? {
        playlistVM.channels.first { $0.tvgId == match.channelTvgID }
    }
}

// MARK: - Hero header

struct MatchDayHero: View {
    let sport: SportCategory
    let totalCount: Int
    let liveCount: Int

    private var dateString: String {
        let f = DateFormatter(); f.dateFormat = "EEEE d MMM"
        return f.string(from: Date())
    }

    var body: some View {
        HStack(spacing: NS.Spacing.lg) {
            VStack(alignment: .leading, spacing: 2) {
                Text(sport.label)
                    .font(NS.Font.heading)
                    .foregroundStyle(NS.text)
                Text("\(dateString) · \(totalCount) matches")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
            }

            if liveCount > 0 {
                HStack(spacing: NS.Spacing.xs) {
                    NSPulseDot()
                    Text("\(liveCount) LIVE")
                        .font(NS.Font.monoSm)
                        .fontWeight(.bold)
                        .kerning(0.6)
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, NS.Spacing.md)
                .padding(.vertical, NS.Spacing.xs)
                .background(NS.live)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
            }

            Spacer()
        }
        .padding(.horizontal, NS.Spacing.xl)
        .frame(height: 56)
        .background(NS.surface)
    }
}

// MARK: - Match card

struct MatchCard: View {
    let match: MatchDayScreen.MatchItem
    let onTap: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: NS.Spacing.md) {

                // Competition
                HStack(spacing: NS.Spacing.sm) {
                    if match.programme.isNow { NSPulseDot() }
                    Text(match.competition.isEmpty ? "MATCH" : match.competition.uppercased())
                        .font(NS.Font.monoSm)
                        .kerning(1.2)
                        .foregroundStyle(NS.text3)
                        .lineLimit(1)
                }

                // Teams + score
                HStack(spacing: NS.Spacing.sm) {
                    Text(match.homeTeam)
                        .font(NS.Font.cardTitle)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    scoreBox

                    Text(match.awayTeam)
                        .font(NS.Font.cardTitle)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }

                // Progress (live only)
                if match.programme.isNow {
                    NSProgressBar(value: match.programme.progress, height: 2, glow: true)
                }

                // Footer
                HStack {
                    Text(timeLabel)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(match.programme.isNow ? NS.accent2 : NS.text3)
                    Spacer()
                    Text(match.channelTvgID)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                        .padding(.horizontal, NS.Spacing.sm)
                        .padding(.vertical, 2)
                        .background(NS.bg)
                        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                        .overlay(RoundedRectangle(cornerRadius: NS.Radius.sm).stroke(NS.border))
                }
            }
            .padding(NS.Spacing.md)
        }
        .buttonStyle(.plain)
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(cardBorder)
        .offset(y: isHovered ? -1 : 0)
        .shadow(color: .black.opacity(isHovered ? 0.31 : 0), radius: 12, y: 8)
        .animation(.easeOut(duration: 0.12), value: isHovered)
        .onHover { isHovered = $0 }
    }

    @ViewBuilder
    private var scoreBox: some View {
        let isLive = match.programme.isNow
        Text(isLive ? "0 – 0" : "vs")
            .font(isLive ? NS.Font.display : NS.Font.caption)
            .foregroundStyle(isLive ? NS.accent2 : NS.text3)
            .frame(minWidth: 52)
            .padding(.horizontal, NS.Spacing.sm)
            .padding(.vertical, NS.Spacing.xs)
            .background(NS.bg)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.md)
                    .stroke(isLive ? NS.accentBorder : NS.border2, lineWidth: 0.5)
            )
            .shadow(color: isLive ? NS.accent.opacity(0.2) : .clear, radius: 8)
    }

    @ViewBuilder
    private var cardBackground: some View {
        switch match.variant {
        case .live:     NS.liveCardGradient
        case .featured: NS.activeCardGradient
        case .ucl:      NS.uclCardGradient
        case .plain:    NS.surface2
        }
    }

    private var cardBorder: some View {
        RoundedRectangle(cornerRadius: NS.Radius.lg)
            .stroke(
                match.variant == .live     ? Color(hex: "ef4444").opacity(0.19) :
                match.variant == .featured ? NS.accentBorder :
                match.variant == .ucl      ? Color(hex: "3b82f6").opacity(0.25) :
                isHovered ? NS.border3 : NS.border,
                lineWidth: 0.5
            )
    }

    private var timeLabel: String {
        if match.programme.isNow {
            let elapsed = Int(Date().timeIntervalSince(match.programme.start) / 60)
            return "\(elapsed)' · Live"
        }
        let f = DateFormatter(); f.dateFormat = "HH:mm"
        return f.string(from: match.programme.start)
    }
}
