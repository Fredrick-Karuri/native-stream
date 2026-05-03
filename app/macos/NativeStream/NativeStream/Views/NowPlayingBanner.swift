// NowPlayingBanner.swift
// Top-of-player banner: channel name, current programme, progress bar.

import SwiftUI

struct NowPlayingBanner: View {

    @Environment(EPGViewModel.self) private var epgVM
    let channel: Channel

    var body: some View {
        HStack(spacing: 12) {
            // Channel logo
            AsyncImage(url: channel.logoURL) { phase in
                if case .success(let img) = phase {
                    img.resizable().scaledToFit()
                } else {
                    Image(systemName: "tv").foregroundStyle(.white.opacity(0.6))
                }
            }
            .frame(width: 28, height: 28)
            .clipShape(RoundedRectangle(cornerRadius: 4))

            VStack(alignment: .leading, spacing: 2) {
                Text(channel.name)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)

                if let programme = epgVM.currentProgramme(for: channel) {
                    HStack(spacing: 8) {
                        Text(programme.title)
                            .font(.system(size: 11))
                            .foregroundStyle(.white.opacity(0.75))
                            .lineLimit(1)

                        Text("·")
                            .foregroundStyle(.white.opacity(0.4))

                        Text("\(programme.startTimeString) – \(programme.stopTimeString)")
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundStyle(.white.opacity(0.5))
                    }

                    ProgressView(value: programme.progress)
                        .progressViewStyle(.linear)
                        .tint(.white.opacity(0.8))
                        .frame(maxWidth: 200)
                }
            }

            Spacer()

            // Live badge
            LiveBadge()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            LinearGradient(
                colors: [.black.opacity(0.7), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
}

struct LiveBadge: View {
    @State private var pulsing = false

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(.red)
                .frame(width: 6, height: 6)
                .opacity(pulsing ? 0.4 : 1.0)
                .animation(.easeInOut(duration: 0.9).repeatForever(), value: pulsing)
            Text("LIVE")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.red.opacity(0.25))
        .clipShape(Capsule())
        .onAppear { pulsing = true }
    }
}