// MatchHeroCard.swift — UX-010
// Full-width card for the most prominent live match.

import SwiftUI

struct MatchHeroCard: View {
    let channel: Channel
    let programme: Programme
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {

                // Art area
                ZStack {
                    NS.surface2
                    teamRow
                }
                .frame(maxWidth: .infinity)
                .frame(height: 120)
                .clipped()

                // Footer
                VStack(alignment: .leading, spacing: NS.Spacing.xs) {
                    HStack(spacing: NS.Spacing.sm) {
                        NSLiveBadge(isLive: true)
                        Text(leagueHint)
                            .font(NS.Font.caption)
                            .foregroundStyle(NS.text3)
                            .lineLimit(1)
                    }
                    Text(programme.title)
                        .font(NS.Font.cardTitle)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)
                    Text(channel.name)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                    NSProgressBar(value: programme.progress, height: 2, glow: false)
                }
                .padding(NS.Spacing.md)
            }
        }
        .buttonStyle(.plain)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.xl))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.xl)
                .stroke(Color(hex: "ef4444").opacity(0.157), lineWidth: 0.5)
        )
    }

    // Two team badge circles + vs
    private var teamRow: some View {
        HStack(spacing: NS.Spacing.lg) {
            teamBadge(initials: leftTeam, size: 40)
            VStack(spacing: 2) {
                Text("LIVE")
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.live)
                    .fontWeight(.bold)
            }
            teamBadge(initials: rightTeam, size: 40)
        }
    }

    private func teamBadge(initials: String, size: CGFloat) -> some View {
        ZStack {
            Circle()
                .fill(NS.surface3)
                .overlay(Circle().stroke(NS.border2, lineWidth: 0.5))
            Text(initials)
                .font(NS.Font.label)
                .foregroundStyle(NS.text2)
        }
        .frame(width: size, height: size)
    }

    // Extract left/right team initials from "Team A vs Team B"
    private var leftTeam: String  { teamInitials(side: 0) }
    private var rightTeam: String { teamInitials(side: 1) }

    private func teamInitials(side: Int) -> String {
        let parts = programme.title
            .components(separatedBy: " vs ")
            .map { $0.trimmingCharacters(in: .whitespaces) }
        guard parts.count == 2 else {
            return side == 0
                ? String(programme.title.prefix(3)).uppercased()
                : channel.name.prefix(3).uppercased()
        }
        return parts[side]
            .components(separatedBy: " ")
            .prefix(2)
            .compactMap { $0.first.map { String($0) } }
            .joined()
            .uppercased()
    }

    private var leagueHint: String {
        // Use groupTitle as a league hint if available
        channel.groupTitle.isEmpty ? "Live" : channel.groupTitle
    }
}
