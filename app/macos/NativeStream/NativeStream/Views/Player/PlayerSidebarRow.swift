// PlayerSidebarRow.swift — UX-021
// Single row in the player's On Now sidebar tab.

import SwiftUI

struct PlayerSidebarRow: View {

    @Environment(EPGViewModel.self)    private var epgVM
    @Environment(PlayerViewModel.self) private var playerVM

    let channel: Channel

    private var isPlaying: Bool { playerVM.currentChannel?.id == channel.id }
    private var current: Programme? { epgVM.currentProgramme(for: channel) }
    private var next: Programme?    { epgVM.nextProgramme(for: channel) }
    private var isLive: Bool        { current != nil }

    var body: some View {
        Button {
            playerVM.play(channel: channel)
        } label: {
            HStack(spacing: NS.Spacing.sm) {

                ChannelLogoSquare(
                    channel: channel,
                    size: 32,
                    cornerRadius: NS.Radius.sm
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.name)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(Color.white.opacity(isPlaying ? 0.9 : 0.6))
                        .lineLimit(1)

                    if let prog = current ?? next {
                        Text(prog.title)
                            .font(NS.Font.monoSm)
                            .foregroundStyle(Color.white.opacity(0.3))
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Right indicator
                if isPlaying {
                    Image(systemName: "play.fill")
                        .font(.system(size: 9))
                        .foregroundStyle(NS.accent)
                } else if isLive {
                    NSPulseDot()
                } else if let next {
                    Text(next.startTimeString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.accent.opacity(0.7))
                }
            }
            .padding(.horizontal, NS.Spacing.sm)
            .padding(.vertical, NS.Spacing.xs)
            .background(isPlaying ? NS.accentGlow : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        }
        .buttonStyle(.plain)
    }
}
