/// Now/MatchesLiveSection.swift
///
/// "Matches live" section for NowScreen. Renders a hero card for the
/// first live match and a small grid for any remaining matches.
/// Assumes items is non-empty — caller is responsible for the guard.

import SwiftUI

struct MatchesLiveSection: View {

    let items: [(channel: Channel, programme: Programme)]
    let onSelectChannel: (Channel) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                NSPulseDot()
                NSGroupHeader(title: "Matches live", count: items.count)
            }

            // Hero card — first match always promoted
            if let first = items.first {
                MatchHeroCard(channel: first.channel, programme: first.programme) {
                    onSelectChannel(first.channel)
                }
            }

            // Small grid — overflow matches
            if items.count > 1 {
                MatchSmallGrid(
                    items: Array(items.dropFirst()),
                    onSelectChannel: onSelectChannel
                )
            }
        }
    }
}
