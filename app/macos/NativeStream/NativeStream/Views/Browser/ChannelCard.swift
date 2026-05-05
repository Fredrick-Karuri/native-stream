import SwiftUI


// MARK: - Channel Card (UX-012)

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

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                // Logo area
                ZStack(alignment: .topTrailing) {
                    ChannelLogoView(channel: channel)
                        .frame(maxWidth: .infinity)
                        .aspectRatio(16/9, contentMode: .fit)

                    if isLive {
                        Text("LIVE")
                            .font(.system(size: 8, weight: .bold))
                            .kerning(0.6)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(NS.live)
                            .clipShape(RoundedRectangle(cornerRadius: 3))
                            .padding(5)
                    }
                }

                // Name
                Text(channel.name)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text)
                    .lineLimit(1)

                // Programme / EPG
                if let prog = programme {
                    Text(prog.title)
                        .font(.system(size: 10))
                        .foregroundStyle(NS.accent2)
                        .lineLimit(1)
                    NSProgressBar(value: prog.progress, height: 2, glow: false)
                } else if let next = epgVM.nextProgramme(for: channel) {
                    Text(next.title)
                        .font(.system(size: 10))
                        .foregroundStyle(NS.text3)
                        .lineLimit(1)
                }

                // Footer
                HStack {
                    NSQualityBadge(quality: "720p")
                    Spacer()
                    NSHealthDot(score: 0.9)
                    Button {
                        favourites.toggle(channel)
                    } label: {
                        Image(systemName: favourites.isFavourite(channel) ? "star.fill" : "star")
                            .font(.system(size: 11))
                            .foregroundStyle(favourites.isFavourite(channel) ? NS.accent : NS.text3)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(12)
        }
        .buttonStyle(.plain)
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(cardBorder)
        .offset(y: isHovered ? -1 : 0)
        .shadow(color: .black.opacity(isHovered ? 0.31 : 0), radius: isHovered ? 12 : 0, y: 8)
        .animation(.easeOut(duration: 0.12), value: isHovered)
        .onHover { isHovered = $0 }
    }

    @ViewBuilder
    private var cardBackground: some View {
        if isPlaying {
            NS.activeCardGradient
        } else if isLive {
            NS.liveCardGradient
        } else if isHovered {
            NS.surface3
        } else {
            NS.surface2
        }
    }

private var cardBorder: some View {
    RoundedRectangle(cornerRadius: NS.Radius.lg)
        .stroke(
            isPlaying ? NS.accentBorder :
            isLive    ? Color(hex: "ef4444").opacity(0.157) :
            isHovered ? NS.border3 : NS.border,
            lineWidth: 0.5
        )
}
}

// MARK: - Channel Logo

struct ChannelLogoView: View {
    let channel: Channel

    var body: some View {
        Group {
            if let url = channel.logoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFit()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity) // ✅ fill the parent
        .background(NS.bg)
    }

    private var placeholder: some View {
        ZStack {
            NS.bg
            Text(channel.name.prefix(3).uppercased())
                .font(NS.Font.label)
                .foregroundStyle(NS.text3)
        }
    }
}