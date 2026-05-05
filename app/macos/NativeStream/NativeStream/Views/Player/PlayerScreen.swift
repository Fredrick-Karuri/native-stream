// PlayerScreen.swift — UX-020, UX-021
// Screen 2: Full-window player with overlay controls and match score overlay.

import SwiftUI
import AVFoundation
import AVKit
import Combine

struct PlayerScreen: View {

    @Environment(PlayerViewModel.self)  private var playerVM
    @Environment(EPGViewModel.self)     private var epgVM

    let channel: Channel?
    let onBack: () -> Void

    @State private var showControls = true
    @State private var hideTask: Task<Void, Never>? = nil


    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Video
            if let player = playerVM.player {
                AVPlayerRepresentableRef(player: player) { nsView in
                    playerVM.setupPiP(playerLayer: nsView.avPlayerLayer)
                }
                .ignoresSafeArea()
            }

            // Field texture overlay (subtle)
            FieldTexture().opacity(0.025).ignoresSafeArea().allowsHitTesting(false)

            // Vignette
            RadialGradient(colors: [.clear, .black.opacity(0.55)],
                           center: .center, startRadius: 100, endRadius: 500)
                .ignoresSafeArea().allowsHitTesting(false)

            // Ambient blue glow
            RadialGradient(colors: [NS.accent.opacity(0.06), .clear],
                           center: .top, startRadius: 0, endRadius: 400)
                .ignoresSafeArea().allowsHitTesting(false)

            // Top bar
            if showControls || playerVM.error != nil {
                VStack {
                    PlayerTopBar(channel: channel, programme: currentProgramme, onBack: onBack)
                    Spacer()
                }
                .transition(.opacity)
            }

            // Match score overlay
            if let prog = currentProgramme, let score = parseScore(prog.title) {
                MatchScoreOverlay(programme: prog, score: score)
            }

            // Error overlay
            if let error = playerVM.error {
                PlayerErrorOverlay(error: error, onRetry: { playerVM.retry() })
            }

            // Bottom controls
            if showControls || playerVM.error != nil {
                VStack {
                    Spacer()
                    PlayerControls(pipController: playerVM.pipController)
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showControls)
        .contentShape(Rectangle())
        .onTapGesture { showControlsTemporarily() }
        .onHover { if $0 { showControlsTemporarily() } }
    }

    private var currentProgramme: Programme? {
        guard let ch = channel else { return nil }
        return epgVM.currentProgramme(for: ch)
    }

    private func showControlsTemporarily() {
        hideTask?.cancel()
        withAnimation { showControls = true }
        hideTask = Task {
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            await MainActor.run { withAnimation { showControls = false } }
        }
    }

    // Parse "Team A vs Team B — Competition" for score overlay
    private func parseScore(_ title: String) -> (home: Int, away: Int)? {
        // Look for pattern like "1 – 0" or "1-0"
        let pattern = #"(\d+)\s*[–\-]\s*(\d+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
              let r1 = Range(match.range(at: 1), in: title),
              let r2 = Range(match.range(at: 2), in: title),
              let home = Int(title[r1]),
              let away = Int(title[r2]) else { return nil }
        return (home, away)
    }
}

// MARK: - Top bar

struct PlayerTopBar: View {
    let channel: Channel?
    let programme: Programme?
    let onBack: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white.opacity(0.1)))
            }
            .buttonStyle(.plain)
            .padding(.top, 2)

            VStack(alignment: .leading, spacing: 2) {
                Text(channel?.name ?? "")
                    .font(NS.Font.heading)
                    .foregroundStyle(.white)
                if let prog = programme {
                    Text(prog.title)
                        .font(NS.Font.caption)
                        .foregroundStyle(Color.white.opacity(0.5))
                }
            }

            Spacer()

            HStack(spacing: 6) {
                NSLiveBadge()
                // NSQualityBadge(quality: "720p")
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.top, 16)
        .padding(.bottom, 20)
        .background(NS.playerTopGradient)
    }
}

// MARK: - Match score overlay (UX-021)

struct MatchScoreOverlay: View {
    let programme: Programme
    let score: (home: Int, away: Int)

    private var teams: (home: String, away: String) {
        let parts = programme.title.components(separatedBy: " vs ")
        if parts.count >= 2 {
            let home = parts[0].trimmingCharacters(in: .whitespaces)
            let away = parts[1].components(separatedBy: " — ").first?
                .trimmingCharacters(in: .whitespaces) ?? parts[1]
            return (home, away)
        }
        return ("Home", "Away")
    }

    private var competition: String {
        if let range = programme.title.range(of: " — ") {
            return String(programme.title[range.upperBound...])
        }
        return ""
    }

    var body: some View {
        VStack(spacing: 20) {
            if !competition.isEmpty {
                Text(competition.uppercased())
                    .font(NS.Font.label)
                    .kerning(1.4)
                    .foregroundStyle(Color.white.opacity(0.3))
            }

            HStack(alignment: .center, spacing: 28) {
                TeamBlock(name: teams.home, emoji: "⚽")
                ScoreBlock(home: score.home, away: score.away, minute: currentMinute)
                TeamBlock(name: teams.away, emoji: "⚽")
            }
        }
    }

    private var currentMinute: Int {
        let elapsed = Date().timeIntervalSince(programme.start)
        return min(90, max(0, Int(elapsed / 60)))
    }
}

struct TeamBlock: View {
    let name: String
    let emoji: String

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.05))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.1)))
                Text(emoji).font(.system(size: 22))
            }
            .frame(width: 52, height: 52)

            Text(name)
                .font(NS.Font.cardTitle)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 120)
        }
    }
}

struct ScoreBlock: View {
    let home: Int
    let away: Int
    let minute: Int

    var body: some View {
        VStack(spacing: 8) {
            Text("\(home) – \(away)")
                .font(NS.Font.scoreXL)
                .foregroundStyle(.white)
                .shadow(color: NS.accent.opacity(0.3), radius: 20)

            Text("\(minute)'")
                .font(NS.Font.mono)
                .foregroundStyle(NS.accent2)
                .padding(.horizontal, 10)
                .padding(.vertical, 2)
                .background(NS.accentGlow)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(NS.accentBorder))
        }
    }
}

// MARK: - Player controls

struct PlayerControls: View {

    @Environment(PlayerViewModel.self) private var playerVM
    var pipController: AVPictureInPictureController?

    var body: some View {
        VStack(spacing: 12) {
            // Progress
            PlayerProgressBar()

            // Control row
            HStack(spacing: 10) {
                CtrlButton(icon: "backward.end.fill", size: 14) { }

                CtrlButton(
                    icon: playerVM.isPlaying ? "pause.fill" : "play.fill",
                    size: 16,
                    isPrimary: true
                ) { playerVM.togglePlayback() }

                CtrlButton(icon: "forward.end.fill", size: 14) { }

                Spacer()

                // Quality pill
                Menu {
                    ForEach(StreamQuality.presets, id: \.displayName) { q in
                        Button(q.displayName) { playerVM.setQuality(q) }
                    }
                } label: {
                    Text(playerVM.quality.displayName)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(Color.white.opacity(0.6))
                        .padding(.horizontal, 10)
                        .frame(height: 32)
                        .background(Color.white.opacity(0.07))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white.opacity(0.08)))
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

                // PiP
                if let pip = pipController {
                    CtrlButton(icon: "rectangle.inset.bottomright.filled", size: 14) {
                        pip.startPictureInPicture()
                    }
                }

                // AirPlay
                AVRoutePickerViewRepresentable()
                    .frame(width: 36, height: 36)
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.top, 32)
        .padding(.bottom, 20)
        .background(NS.playerBottomGradient)
    }
}

struct CtrlButton: View {
    let icon: String
    let size: CGFloat
    var isPrimary = false
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size, weight: .medium))
                .foregroundStyle(isPrimary ? NS.bg : (isHovered ? .white : Color.white.opacity(0.7)))
                .frame(width: isPrimary ? 44 : 36, height: isPrimary ? 44 : 36)
                .background(
                    isPrimary ? NS.accent :
                    isHovered ? Color.white.opacity(0.12) : Color.white.opacity(0.07)
                )
                .clipShape(RoundedRectangle(cornerRadius: isPrimary ? 11 : 9))
                .overlay(RoundedRectangle(cornerRadius: isPrimary ? 11 : 9)
                    .stroke(Color.white.opacity(isPrimary ? 0 : 0.08)))
                .shadow(color: isPrimary ? NS.accent.opacity(0.5) : .clear, radius: 10)
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

// MARK: - Progress bar

struct PlayerProgressBar: View {
    @Environment(PlayerViewModel.self) private var playerVM
    @State private var progress: Double = 0.38
    @State private var isHovering = false
    let timer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 6) {
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.white.opacity(0.1))
                    .frame(height: isHovering ? 4 : 3)

                RoundedRectangle(cornerRadius: 2)
                    .fill(NS.accent)
                    .frame(width: .infinity * progress, height: isHovering ? 4 : 3)
                    .shadow(color: NS.accent.opacity(0.6), radius: 4)
            }
            .frame(height: 4)
            .animation(.easeOut(duration: 0.12), value: isHovering)
            .onHover { isHovering = $0 }
        }
        .onReceive(timer) { _ in } // trigger view refresh for progress updates
    }
}

// MARK: - Error overlay

struct PlayerErrorOverlay: View {
    let error: PlayerError
    let onRetry: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.75)
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 36))
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
                Button("Retry", action: onRetry)
                    .buttonStyle(.borderedProminent)
                    .tint(NS.amber)
            }
            .padding(32)
        }
        .ignoresSafeArea()
    }
}

// MARK: - Field texture

struct FieldTexture: View {
    var body: some View {
        Canvas { ctx, size in
            let step: CGFloat = 60
            ctx.withCGContext { cg in
                cg.setStrokeColor(CGColor(red: 0.3, green: 0.87, blue: 0.5, alpha: 1))
                cg.setLineWidth(1)
                var x: CGFloat = 0
                while x <= size.width { cg.move(to: CGPoint(x: x, y: 0)); cg.addLine(to: CGPoint(x: x, y: size.height)); x += step }
                var y: CGFloat = 0
                while y <= size.height { cg.move(to: CGPoint(x: 0, y: y)); cg.addLine(to: CGPoint(x: size.width, y: y)); y += step }
                cg.strokePath()
            }
        }
    }
}
