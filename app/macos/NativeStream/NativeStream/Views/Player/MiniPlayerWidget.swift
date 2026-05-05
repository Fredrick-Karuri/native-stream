// MiniPlayerWidget.swift — UX-040
// Floating mini player shown while browsing with active playback.

import SwiftUI

struct MiniPlayerWidget: View {

    @Environment(PlayerViewModel.self)  private var playerVM
    @Environment(EPGViewModel.self)     private var epgVM

    let onExpand: () -> Void
    @State private var isHovered = false

    var body: some View {
        VStack(spacing: 0) {
            // Mini video area
            ZStack {
                NS.bg
                FieldTexture().opacity(0.04)

                // Score if available
                if let ch = playerVM.currentChannel,
                   let prog = epgVM.currentProgramme(for: ch) {
                    Text(extractScore(prog.title) ?? "● Live")
                        .font(NS.Font.scoreXL.leading(.tight))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.5), radius: 8)
                }

                // LIVE badge
                VStack {
                    HStack {
                        NSLiveBadge()
                        Spacer()
                        // Expand button
                        Button(action: onExpand) {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 9))
                                .foregroundStyle(Color.white.opacity(0.6))
                                .frame(width: 22, height: 22)
                                .background(Color.black.opacity(0.5))
                                .clipShape(RoundedRectangle(cornerRadius: 5))
                                .overlay(RoundedRectangle(cornerRadius: 5)
                                    .stroke(Color.white.opacity(0.1)))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(8)
                    Spacer()
                }
            }
            .frame(height: 140)
            .clipShape(Rectangle())

            // Info + controls
            VStack(alignment: .leading, spacing: 8) {
                if let ch = playerVM.currentChannel {
                    Text(ch.name)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)

                    if let prog = epgVM.currentProgramme(for: ch) {
                        Text("\(prog.title) · \(minuteStr(prog))")
                            .font(.system(size: 10))
                            .foregroundStyle(NS.text3)
                            .lineLimit(1)
                    }
                }

                HStack(spacing: 6) {
                    MiniCtrl(icon: "backward.end.fill") { }
                    MiniCtrl(icon: playerVM.isPlaying ? "pause.fill" : "play.fill", isPrimary: true) {
                        playerVM.togglePlayback()
                    }
                    MiniCtrl(icon: "forward.end.fill") { }

                    // Progress
                    if let ch = playerVM.currentChannel,
                       let prog = epgVM.currentProgramme(for: ch) {
                        NSProgressBar(value: prog.progress, height: 2)
                    } else {
                        NSProgressBar(value: 0.38, height: 2)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(NS.surface)
        }
        .frame(width: 256)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.xl).stroke(NS.border2))
        .shadow(color: .black.opacity(0.55), radius: 24, y: 12)
        .shadow(color: NS.accent.opacity(0.07), radius: 20)
    }

    private func extractScore(_ title: String) -> String? {
        let p = #"(\d+)\s*[–\-]\s*(\d+)"#
        guard let r = try? NSRegularExpression(pattern: p),
              let m = r.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
              let r1 = Range(m.range(at: 1), in: title),
              let r2 = Range(m.range(at: 2), in: title) else { return nil }
        return "\(title[r1]) – \(title[r2])"
    }

    private func minuteStr(_ prog: Programme) -> String {
        let mins = Int(Date().timeIntervalSince(prog.start) / 60)
        return "\(max(0, mins))'"
    }
}

struct MiniCtrl: View {
    let icon: String
    var isPrimary = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(isPrimary ? NS.bg : NS.text2)
                .frame(width: 26, height: 26)
                .background(isPrimary ? NS.accent : NS.surface2)
                .clipShape(RoundedRectangle(cornerRadius: 7))
                .overlay(RoundedRectangle(cornerRadius: 7).stroke(isPrimary ? NS.accent : NS.border))
        }
        .buttonStyle(.plain)
    }
}