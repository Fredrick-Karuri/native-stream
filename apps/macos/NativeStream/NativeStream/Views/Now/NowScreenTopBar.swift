/// Now/NowScreenTopBar.swift
///
/// Top bar for NowScreen. Displays the screen title and a live/soon
/// summary count. Receives plain Int counts so it stays trivially
/// testable and independent of the view-model.

import SwiftUI

struct NowScreenTopBar: View {

    let liveCount: Int
    let soonCount: Int

    var body: some View {
        HStack(spacing: NS.Spacing.sm) {
            Text("What's on")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)
            Spacer()
            Text("\(liveCount) live · \(soonCount) soon")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }
}
