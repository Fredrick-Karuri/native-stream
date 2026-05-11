// MatchSmallCard.swift — UX-011
// 2-column grid of remaining live matches after the hero card.

import SwiftUI

// MARK: - Grid wrapper

struct MatchSmallGrid: View {
    let items: [(channel: Channel, programme: Programme)]
    let onSelectChannel: (Channel) -> Void

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.flexible(), spacing: NS.Spacing.sm),
                      GridItem(.flexible(), spacing: NS.Spacing.sm)],
            spacing: NS.Spacing.sm
        ) {
            ForEach(items, id: \.channel.id) { item in
                MatchSmallCard(channel: item.channel, programme: item.programme) {
                    onSelectChannel(item.channel)
                }
            }
        }
    }
}

// MARK: - Card

struct MatchSmallCard: View {
    let channel: Channel
    let programme: Programme
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {

                // Art area
                ZStack {
                    NS.surface2
                    HStack(spacing: NS.Spacing.md) {
                        teamBadge(initials: leftTeam)
                        VStack(spacing: 2) {
                            Text("LIVE")
                                .font(NS.Font.monoSm)
                                .foregroundStyle(NS.live)
                                .fontWeight(.bold)
                        }
                        teamBadge(initials: rightTeam)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 72)

                // Footer
                VStack(alignment: .leading, spacing: 2) {
                    Text(programme.title)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)
                    Text(channel.name)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                        .lineLimit(1)
                    NSProgressBar(value: programme.progress, height: 2, glow: false)
                }
                .padding(NS.Spacing.sm)
            }
        }
        .buttonStyle(.plain)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(Color(hex: "ef4444").opacity(0.157), lineWidth: 0.5)
        )
    }

    private func teamBadge(initials: String) -> some View {
        ZStack {
            Circle()
                .fill(NS.surface3)
                .overlay(Circle().stroke(NS.border2, lineWidth: 0.5))
            Text(initials)
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text2)
        }
        .frame(width: 28, height: 28)
    }

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
}
