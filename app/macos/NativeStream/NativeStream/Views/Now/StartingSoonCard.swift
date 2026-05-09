// StartingSoonCard.swift — UX-013
// 3-column grid of upcoming events starting within 2 hours.

import SwiftUI

// MARK: - Grid wrapper

struct StartingSoonGrid: View {
    let items: [(channel: Channel, programme: Programme)]
    let onSelectChannel: (Channel) -> Void

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.flexible(), spacing: NS.Spacing.sm),
                      GridItem(.flexible(), spacing: NS.Spacing.sm),
                      GridItem(.flexible(), spacing: NS.Spacing.sm)],
            spacing: NS.Spacing.sm
        ) {
            ForEach(items, id: \.channel.id) { item in
                StartingSoonCard(channel: item.channel, programme: item.programme) {
                    onSelectChannel(item.channel)
                }
            }
        }
    }
}

// MARK: - Card

struct StartingSoonCard: View {
    let channel: Channel
    let programme: Programme
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {

                // Kick-off time
                Text(programme.startTimeString)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.accent)

                // Team badges if match, else channel logo abbr
                HStack(spacing: NS.Spacing.xs) {
                    teamBadge(initials: leftTeam)
                    Text("vs")
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                    teamBadge(initials: rightTeam)
                }

                // Title
                Text(programme.title)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text)
                    .lineLimit(2)

                // Channel
                Text(channel.name)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.text3)
                    .lineLimit(1)
            }
            .padding(NS.Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(NS.border, lineWidth: 0.5)
        )
    }

    private func teamBadge(initials: String) -> some View {
        ZStack {
            Circle()
                .fill(NS.surface3)
                .overlay(Circle().stroke(NS.border2, lineWidth: 0.5))
            Text(initials)
                .font(.system(size: 7, weight: .medium))
                .foregroundStyle(NS.text2)
        }
        .frame(width: 20, height: 20)
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
