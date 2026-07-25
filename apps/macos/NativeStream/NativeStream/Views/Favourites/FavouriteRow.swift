import SwiftUI

// MARK: - Favourite Row

struct FavouriteRow: View {

    @Environment(FavouritesManager.self) private var favourites

    let channel: Channel
    let programme: Programme?
    let isPlaying: Bool
    let isUpcoming: Bool
    let onTap: () -> Void

    @State private var isHovered = false

    private var isLive: Bool { programme?.isNow ?? false }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: NS.Spacing.md) {

                ChannelLogoSquare(channel: channel, size: NS.Favourites.logoSize)

                // Body
                VStack(alignment: .leading, spacing: NS.Favourites.textSpacing) {
                    Text(channel.name)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)

                    if isLive, let teams = teamsFromTitle {
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
                        } else if let prog = programme {
                            Text(prog.title)
                                .font(NS.Font.caption)
                                .foregroundStyle(NS.text3)
                                .lineLimit(1)
                        } else {
                            Text("No programme info")
                                .font(NS.Font.caption)
                                .foregroundStyle(NS.text3.opacity(0.5))
                                .lineLimit(1)
                        }

                        if isLive, let prog = programme {
                            NSProgressBar(value: prog.progress, height: NS.Favourites.progressBarHeight, glow: false)
                        }
}
                .frame(maxWidth: .infinity, alignment: .leading)

                // Right badge
                if isPlaying {
                    playingBadge
                } else if isLive, let prog = programme {
                    Text(prog.timeRemainingString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                } else if let prog = programme {
                    Text(prog.startTimeString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.accent)
                }

                // Star
                Button {
                    favourites.toggle(channel)
                } label: {
                    Image(systemName: "star.fill")
                        .font(.system(size: NS.Favourites.starIconSize))
                        .foregroundStyle(NS.amber)
                }
                .buttonStyle(.plain)
            }
            .padding(NS.Spacing.md)
            .background(isHovered ? NS.surface3 : NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.lg)
                    .stroke(rowBorderColour, lineWidth: NS.Favourites.borderWidth)
            )
            .contentShape(.interaction, Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
        }
    }

    private var playingBadge: some View {
        HStack(spacing: NS.Favourites.textSpacing) {
            Image(systemName: "play.fill")
                .font(.system(size: NS.Favourites.playIconSize))
            Text("NOW")
                .font(NS.Font.monoSm)
                .fontWeight(.bold)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, NS.Spacing.sm)
        .padding(.vertical, NS.Favourites.badgePaddingV)
        .background(NS.accentGlow)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
    }

    private var rowBorderColour: Color {
        if isPlaying { return NS.accentBorder }
        if isHovered {
            return isLive ? NS.live.opacity(NS.Favourites.liveBorderOpacityHover) : NS.border2
        }
        return isLive ? NS.live.opacity(NS.Favourites.liveBorderOpacityIdle) : NS.border
    }

    private var teamsFromTitle: (home: String, away: String)? {
        guard let programme else { return nil }
        let parts = programme.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return nil }
        let home = parts[0].trimmingCharacters(in: .whitespaces)
        let away = parts[1]
            .components(separatedBy: " — ")[0]
            .trimmingCharacters(in: .whitespaces)
        return (home, away)
    }
}

