// MatchScoreOverlay.swift

import SwiftUI

// MARK: - Match score overlay

struct MatchScoreOverlay: View {
    let programme: Programme
    let score: (home: Int, away: Int)

    private var teams: (home: String, away: String) {
        let parts = programme.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return ("Home", "Away") }
        return (
            parts[0].trimmingCharacters(in: .whitespaces),
            parts[1].components(separatedBy: " — ").first?.trimmingCharacters(in: .whitespaces) ?? parts[1]
        )
    }

    private var competition: String {
        guard let range = programme.title.range(of: " — ") else { return "" }
        return String(programme.title[range.upperBound...])
    }

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            if !competition.isEmpty {
                Text(competition.uppercased())
                    .font(NS.Font.label).kerning(1.4)
                    .foregroundStyle(Color.white.opacity(0.3))
            }
            HStack(alignment: .center, spacing: NS.Spacing.xxl) {
                TeamBlock(name: teams.home, emoji: "⚽")
                ScoreBlock(home: score.home, away: score.away, minute: currentMinute)
                TeamBlock(name: teams.away, emoji: "⚽")
            }
        }
    }

    private var currentMinute: Int {
        min(90, max(0, Int(Date().timeIntervalSince(programme.start) / 60)))
    }
}

struct TeamBlock: View {
    let name: String
    let emoji: String

    var body: some View {
        VStack(spacing: NS.Spacing.sm) {
            ZStack {
                RoundedRectangle(cornerRadius: NS.Player.teamBadgeRadius)
                    .fill(Color.white.opacity(0.05))
                    .overlay(RoundedRectangle(cornerRadius: NS.Player.teamBadgeRadius)
                    .stroke(Color.white.opacity(0.1)))
                Text(emoji)
                .font(.system(size: NS.Player.teamEmojiSize))
            }
            .frame(width: NS.Player.teamBadgeSize, height: NS.Player.teamBadgeSize)
            Text(name)
                .font(NS.Font.cardTitle).foregroundStyle(.white)
                .multilineTextAlignment(.center).frame(maxWidth: NS.Player.teamNameMaxWidth)
        }
    }
}

struct ScoreBlock: View {
    let home: Int
    let away: Int
    let minute: Int

    var body: some View {
        VStack(spacing: NS.Spacing.sm) {
            Text("\(home) – \(away)")
                .font(NS.Font.scoreXL).foregroundStyle(.white)
                .shadow(color: NS.accent.opacity(0.3), radius: 20)
            Text("\(minute)'")
                .font(NS.Font.mono).foregroundStyle(NS.accent2)
                .padding(.horizontal, NS.Chip.paddingH)
                .padding(.vertical, NS.Spacing.xxs)
                .background(NS.accentGlow)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                .overlay(RoundedRectangle(cornerRadius: NS.Radius.sm).stroke(NS.accentBorder))
        }
    }
}


