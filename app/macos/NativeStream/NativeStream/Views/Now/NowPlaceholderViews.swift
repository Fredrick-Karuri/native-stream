/// Now/NowPlaceholderViews.swift
///
/// Loading and empty-state placeholder views for NowScreen.
/// Kept in one file as they are small, stateless, and always
/// used together as a pair.

import SwiftUI

struct NowLoadingView: View {
    var body: some View {
        VStack(spacing: NS.Spacing.md) {
            ProgressView().scaleEffect(0.8)
            Text("Loading…")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct NowEmptyView: View {
    var body: some View {
        VStack(spacing: NS.Spacing.md) {
            Text("📺").font(.system(size: 36))
            Text("Nothing on right now")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)
            Text("Add a playlist source in Settings or check back later.")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
