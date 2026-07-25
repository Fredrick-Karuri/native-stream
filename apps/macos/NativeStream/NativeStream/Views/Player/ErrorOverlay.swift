//
//  ErrorOverlay.swift
//  NativeStream
//
//  Created by Fredrick Karuri on 23/06/2026.
//

import SwiftUI

struct PlayerErrorOverlay: View {
    let error: PlayerError
    let onRetry: () -> Void
    var onTryWithProxy: (() -> Void)? = nil

    var body: some View {
        ZStack {
            Color.black.opacity(0.75)
            VStack(spacing: NS.Spacing.lg) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: NS.Player.errorIconSize))
                    .foregroundStyle(NS.amber)
                Text(error.errorDescription ?? "Stream unavailable")
                    .font(NS.Font.bodyMedium)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                if let suggestion = error.recoverySuggestion {
                    Text(suggestion)
                        .font(NS.Font.caption)
                        .foregroundStyle(Color.white.opacity(0.5))
                        .multilineTextAlignment(.center)
                }
                HStack(spacing: NS.Spacing.sm) {
                    Button("Retry", action: onRetry)
                        .buttonStyle(.borderedProminent).tint(NS.amber)
                    if let onTryWithProxy {
                        Button("Try with proxy", action: onTryWithProxy)
                            .buttonStyle(.bordered).tint(NS.amber)
                    }
                }
            }
            .padding(NS.Player.errorPadding)
        }
        .ignoresSafeArea()
    }
}
