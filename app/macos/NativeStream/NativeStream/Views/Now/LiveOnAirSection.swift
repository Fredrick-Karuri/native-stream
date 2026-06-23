/// Now/LiveOnAirSection.swift
///
/// "Live on air" section for NowScreen. Shows non-match live channels
/// in a row list, capped at 10 with a show-all toggle. Owns its own
/// expansion state — NowScreen does not need to know about it.

import SwiftUI

private let initialVisibleRowCount = 10

struct LiveOnAirSection: View {

    let items: [(channel: Channel, programme: Programme)]
    let onSelectChannel: (Channel) -> Void

    @State private var showAll = false

    private var visibleItems: [(channel: Channel, programme: Programme)] {
        showAll ? items : Array(items.prefix(initialVisibleRowCount))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: "tv")
                    .font(.system(size: 11))
                    .foregroundStyle(NS.text3)
                NSGroupHeader(title: "Live on air", count: items.count)
            }

            VStack(spacing: NS.Spacing.sm) {
                ForEach(visibleItems, id: \.channel.id) { item in
                    LiveOnAirRow(channel: item.channel, programme: item.programme) {
                        onSelectChannel(item.channel)
                    }
                }
            }

            if items.count > initialVisibleRowCount {
                Button(showAll ? "Show less" : "Show all \(items.count)") {
                    withAnimation(.easeInOut(duration: 0.2)) { showAll.toggle() }
                }
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.accent2)
                .buttonStyle(.plain)
            }
        }
    }
}
