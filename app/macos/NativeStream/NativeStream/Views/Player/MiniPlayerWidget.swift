// MiniPlayerWidget.swift — UX-040
import SwiftUI

struct MiniPlayerWidget: View {

    @Environment(PlayerViewModel.self)  private var playerVM
    @Environment(EPGViewModel.self)     private var epgVM

    let onExpand: () -> Void
    let onClose: () -> Void

    var body: some View {
        let ch = playerVM.currentChannel
        let prog = ch.flatMap { epgVM.currentProgramme(for: $0) }

        VStack(spacing: 0) {
            // Mini video area
            ZStack {
                NS.bg

                if let prog {
                    Text(extractScore(prog.title) ?? "● Live")
                        .font(NS.Font.scoreXL.leading(.tight))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.5), radius: 8)
                }

                VStack {
                    HStack {
                        NSLiveBadge(isLive: prog?.isNow ?? false)
                        Spacer()
                        NSIconButton(icon: "arrow.up.left.and.arrow.down.right", size: 9, isDark: true) { onExpand() }
                        NSIconButton(icon: "xmark", size: 9, isDark: true) { onClose() }
                        
                    }
                    .padding(8)
                    Spacer()
                }
            }
            .frame(height: 140)
            .clipShape(Rectangle())

            // Info + controls
            VStack(alignment: .leading, spacing: 8) {
                if let ch {
                    Text(ch.name)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)

                    if let prog {
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
                    NSProgressBar(value: prog?.progress ?? 0, height: 2)
                    MiniCtrl(icon: playerVM.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill") {
                        playerVM.toggleMute()
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
