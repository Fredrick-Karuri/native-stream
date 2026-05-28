import SwiftUI

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
                    Text(programme.timeRemainingString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
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
