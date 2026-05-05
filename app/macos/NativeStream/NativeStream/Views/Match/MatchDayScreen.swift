// MatchDayScreen.swift — UX-030, UX-031, UX-032
// Screen 3: Match Day — live and upcoming matches from EPG.

import SwiftUI

enum SportFilter: String, CaseIterable {
    case all        = "All"
    case football   = "⚽ Football"
    case rugby      = "🏉 Rugby"
    case basketball = "🏀 NBA"
    case cricket    = "🏏 Cricket"
}

enum MatchCardVariant { case live, featured, ucl, plain }

struct MatchDayScreen: View {

    @Environment(EPGViewModel.self)        private var epgVM
    @Environment(PlaylistViewModel.self)   private var playlistVM

    let onSelectChannel: (Channel) -> Void

    @State private var sportFilter: SportFilter = .all

    var body: some View {
        VStack(spacing: 0) {
            MatchDayHero(
                totalCount: allMatches.count,
                liveCount: liveMatches.count,
                sportFilter: $sportFilter
            )
            Divider().overlay(NS.border)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 28) {
                    // Live now
                    if !liveMatches.isEmpty {
                        matchSection(title: "🔴  Live now", matches: liveMatches)
                    }
                    // Up next
                    if !upcomingMatches.isEmpty {
                        matchSection(title: "⏰  Up next", matches: upcomingMatches)
                    }
                    // Empty state
                    if liveMatches.isEmpty && upcomingMatches.isEmpty {
                        VStack(spacing: 12) {
                            Text("📅").font(.system(size: 40))
                            Text("No matches today").font(NS.Font.display).foregroundStyle(NS.text)
                            Text("Check back closer to kick-off time.")
                                .font(NS.Font.caption).foregroundStyle(NS.text3)
                        }
                        .frame(maxWidth: .infinity).padding(.top, 80)
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, 80)
            }
        }
        .background(NS.bg)
    }

    @ViewBuilder
    private func matchSection(title: String, matches: [MatchItem]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            NSGroupHeader(title: title)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 260), spacing: 8)],
                spacing: 8
            ) {
                ForEach(matches) { match in
                    MatchCard(match: match) {
                        if let ch = channelFor(match) { onSelectChannel(ch) }
                    }
                }
            }
        }
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
                guard let parsed = parseMatch(prog, channelID: ch.tvgId) else { continue }
                if sportFilter != .all {
                    let kw = sportFilter.rawValue.lowercased()
                    guard parsed.competition.lowercased().contains(kw) ||
                          ch.groupTitle.lowercased().contains(kw) else { continue }
                }
                items.append(parsed)
            }
        }
        return items.sorted { $0.programme.start < $1.programme.start }
    }

    private var liveMatches: [MatchItem] {
        allMatches.filter { $0.programme.isNow }
    }

    private var upcomingMatches: [MatchItem] {
        allMatches.filter { !$0.programme.isNow && $0.programme.start > Date() }
    }

    private func parseMatch(_ prog: Programme, channelID: String) -> MatchItem? {
        // Expected title: "Home vs Away — Competition"
        guard prog.title.contains(" vs ") else { return nil }
        let parts = prog.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return nil }
        let home = parts[0].trimmingCharacters(in: .whitespaces)
        let rest = parts[1].components(separatedBy: " — ")
        let away = rest[0].trimmingCharacters(in: .whitespaces)
        let competition = rest.count > 1 ? rest[1].trimmingCharacters(in: .whitespaces) : ""
        
        var variant: MatchCardVariant = prog.isNow ? .live : .plain
        if !prog.isNow && ucl(competition.lowercased()) { variant = .ucl }
        else if !prog.isNow && pl(competition.lowercased()) { variant = .featured }

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

// MARK: - Hero header (UX-030)

struct MatchDayHero: View {
    let totalCount: Int
    let liveCount: Int
    @Binding var sportFilter: SportFilter

    private var dateString: String {
        let f = DateFormatter(); f.dateFormat = "EEEE d MMM"
        return f.string(from: Date())
    }

    var body: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Match Day")
                    .font(NS.Font.display)
                    .foregroundStyle(NS.text)
                Text("\(dateString) · \(totalCount) matches today")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
            }

            if liveCount > 0 {
                HStack(spacing: 5) {
                    NSPulseDot()
                    Text("\(liveCount) LIVE")
                        .font(.system(size: 10, weight: .bold))
                        .kerning(0.6)
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(NS.live)
                .clipShape(RoundedRectangle(cornerRadius: 5))
            }

            Spacer()

            HStack(spacing: 6) {
                ForEach(SportFilter.allCases, id: \.self) { f in
                    NSChip(label: f.rawValue, isActive: sportFilter == f) {
                        sportFilter = f
                    }
                }
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .frame(height: 64)
        .background(NS.surface)
    }
}

// MARK: - Match card (UX-031)

struct MatchCard: View {
    let match: MatchDayScreen.MatchItem
    let onTap: () -> Void

    @State private var isHovered = false

    private typealias MI = MatchDayScreen.MatchItem

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                // Competition label
                HStack(spacing: 6) {
                    if match.programme.isNow { NSPulseDot() }
                    Text(match.competition.uppercased().isEmpty ? "MATCH" : match.competition.uppercased())
                        .font(NS.Font.monoSm)
                        .kerning(1.2)
                        .foregroundStyle(NS.text3)
                }

                // Teams + score
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(match.homeTeam)
                            .font(NS.Font.cardTitle)
                            .foregroundStyle(NS.text)
                        Text(shortName(match.homeTeam))
                            .font(.system(size: 10))
                            .foregroundStyle(NS.text3)
                    }
                    Spacer()
                    scoreBox
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(match.awayTeam)
                            .font(NS.Font.cardTitle)
                            .foregroundStyle(NS.text)
                        Text(shortName(match.awayTeam))
                            .font(.system(size: 10))
                            .foregroundStyle(NS.text3)
                    }
                }

                // Progress bar (live only)
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
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(NS.bg)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(NS.border))
                }
            }
            .padding(14)
        }
        .buttonStyle(.plain)
        .background(cardBg)
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
            .frame(minWidth: 56)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(NS.bg)
            .clipShape(RoundedRectangle(cornerRadius: 7))
            .overlay(
                RoundedRectangle(cornerRadius: 7)
                    .stroke(isLive ? NS.accentBorder : NS.border2)
            )
            .shadow(color: isLive ? NS.accent.opacity(0.2) : .clear, radius: 8)
    }

    @ViewBuilder
    private var cardBg: some View {
        switch match.variant {
        case .live:     Rectangle().fill(NS.liveCardGradient)
        case .featured: Rectangle().fill(NS.activeCardGradient)
        case .ucl:      Rectangle().fill(NS.uclCardGradient)
        case .plain:    Rectangle().fill(NS.surface2)
        }
    }
    private var cardBorder: some View {
        RoundedRectangle(cornerRadius: NS.Radius.lg).stroke(
            match.variant == .live     ? Color(hex: "ef4444").opacity(0.19) :
            match.variant == .featured ? NS.accentBorder :
            match.variant == .ucl      ? Color(hex: "3b82f6").opacity(0.25) :
            isHovered ? NS.border3 : NS.border
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

    private func shortName(_ name: String) -> String {
        name.components(separatedBy: " ").map { String($0.prefix(3)) }.joined(separator: " ")
    }
}

// Helpers for MatchDayScreen

private func ucl(_ s: String) -> Bool {
    s.contains("champions") || s.contains("ucl")
}
private func pl(_ s: String) -> Bool {
    s.contains("premier") || s.contains("liga") || s.contains("bundesliga")
}
