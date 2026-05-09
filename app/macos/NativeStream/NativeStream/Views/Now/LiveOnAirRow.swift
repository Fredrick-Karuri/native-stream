// LiveOnAirRow.swift — UX-012
// Row for channels broadcasting live non-match content (PGA, snooker, studio shows).

import SwiftUI

struct LiveOnAirRow: View {
    let channel: Channel
    let programme: Programme
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: NS.Spacing.md) {

                ChannelLogoSquare(channel: channel, size: 36)

                VStack(alignment: .leading, spacing: 3) {
                    Text(programme.title)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)
                    Text(channel.name)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                    NSProgressBar(value: programme.progress, height: 2, glow: false)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                NSLiveBadge(isLive: true)
            }
            .padding(NS.Spacing.md)
        }
        .buttonStyle(.plain)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(NS.border, lineWidth: 0.5)
        )
    }
}
