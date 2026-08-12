/// Now/StartingSoonSection.swift
///
/// "Starting soon" section for NowScreen. Delegates grid layout to
/// StartingSoonGrid. Assumes items is non-empty — caller guards this.

import SwiftUI

struct StartingSoonSection: View {

    let items: [(channel: Channel, programme: Programme)]
    let onSelectChannel: (Channel) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            HStack(spacing: NS.Spacing.sm) {
                Image(systemName: "clock")
                    .font(.system(size: 11))
                    .foregroundStyle(NS.text3)
                NSGroupHeader(title: "Starting soon", count: items.count)
            }
            StartingSoonGrid(items: items, onSelectChannel: onSelectChannel)
        }
    }
}
