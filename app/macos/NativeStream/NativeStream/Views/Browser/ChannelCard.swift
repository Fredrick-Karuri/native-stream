import SwiftUI

// MARK: - Channel Card (UX-003)

struct ChannelCard: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlayerViewModel.self)   private var playerVM
    @Environment(FavouritesManager.self) private var favourites

    let channel: Channel
    let onTap: () -> Void

    @State private var isHovered = false

    private var isLive: Bool    { epgVM.currentProgramme(for: channel) != nil }
    private var isPlaying: Bool { playerVM.currentChannel?.id == channel.id }
    private var programme: Programme? { epgVM.currentProgramme(for: channel) }

    private var borderColour: Color {
        isPlaying ? NS.accentBorder :
        isLive    ? Color(hex: "ef4444").opacity(0.157) :
                    NS.border
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {

                // Artwork
                ZStack(alignment: .bottom) {
                    ChannelLogoView(channel: channel, borderColour: borderColour)

                    if let prog = programme {
                        NSProgressBar(value: prog.progress, height: 3, glow: false)
                            .clipShape(.rect(
                                bottomLeadingRadius: NS.Radius.lg,
                                bottomTrailingRadius: NS.Radius.lg
                            ))
                    }

                    VStack {
                        HStack(alignment: .top) {
                            if isLive { NSLiveBadge(isLive: true) }
                            Spacer()
                            if isPlaying { playingBadge } else { starButton }
                        }
                        .padding(8)
                        Spacer()
                    }
                }
                .scaleEffect(isHovered ? 1.02 : 1.0)
                .animation(.easeOut(duration: 0.12), value: isHovered)

                // Channel name
                Text(channel.name)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text)
                    .lineLimit(1)

                // EPG line
                if let prog = programme {
                    Text(prog.title)
                        .font(NS.Font.caption)
                        .foregroundStyle(NS.accent2)
                        .lineLimit(1)
                } else if let next = epgVM.nextProgramme(for: channel) {
                    Text(next.title)
                        .font(NS.Font.caption)
                        .foregroundStyle(NS.text3)
                        .lineLimit(1)
                }
            }
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }

    private var playingBadge: some View {
        HStack(spacing: 3) {
            Image(systemName: "play.fill").font(.system(size: 7))
            Text("NOW").font(NS.Font.monoSm).fontWeight(.bold)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(NS.accentGlow)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
        .padding(8)
    }

    private var starButton: some View {
        Button { favourites.toggle(channel) } label: {
            Image(systemName: favourites.isFavourite(channel) ? "star.fill" : "star")
                .font(.system(size: 12))
                .foregroundStyle(favourites.isFavourite(channel) ? NS.amber : .white)
                .shadow(color: .black.opacity(0.5), radius: 3)
        }
        .buttonStyle(.plain)
        .padding(8)
    }
}

// MARK: - Channel Logo (UX-004)

struct ChannelLogoView: View {
    let channel: Channel
    var borderColour: Color = NS.border

    var body: some View {
        Group {
            if let url = channel.logoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .aspectRatio(16/9, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(borderColour, lineWidth: 0.5)
        )
    }

    private var placeholder: some View {
        ZStack {
            NS.surface2
            Text(channel.name.prefix(3).uppercased())
                .font(NS.Font.label)
                .foregroundStyle(NS.text3)
        }
    }
}
