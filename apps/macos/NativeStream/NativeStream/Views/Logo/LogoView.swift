// LogoView.swift
// Reusable NativeStream logo mark. Uses the vector SVG asset from the catalog.

import SwiftUI

struct LogoMark: View {
    var size: CGFloat = 44

    var body: some View {
        Image("LogoMark")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(Circle())
    }
}

// Full lockup: mark + wordmark side by side
struct LogoLockup: View {
    var markSize: CGFloat = 32

    var body: some View {
        HStack(spacing: 10) {
            LogoMark(size: markSize)
            HStack(spacing: 0) {
                Text("Native")
                    .font(NS.Font.heading)
                    .foregroundStyle(NS.text)
                Text("Stream")
                    .font(NS.Font.heading)
                    .foregroundStyle(NS.accent)
            }
        }
    }
}

// Splash / onboarding version — large centred mark
struct LogoSplash: View {
    var body: some View {
        VStack(spacing: 16) {
            LogoMark(size: 96)
            HStack(spacing: 0) {
                Text("Native")
                    .font(NS.Font.display)
                    .foregroundStyle(NS.text)
                Text("Stream")
                    .font(NS.Font.display)
                    .foregroundStyle(NS.accent)
            }
        }
    }
}

#Preview {
    VStack(spacing: 32) {
        LogoMark(size: 96)
        LogoLockup()
        LogoSplash()
    }
    .padding(40)
    .background(NS.bg)
}
