// PlayerScreen.swift — UX-020 + fullscreen fix
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
    @State private var showSidebar  = true
    @State private var hideTask: Task<Void, Never>? = nil

    var body: some View {
        HStack(spacing: 0) {

            // Video area
            ZStack {
                Color.black.ignoresSafeArea()

                if let player = playerVM.player, !playerVM.pipActive {
                    AVPlayerRepresentableRef(player: player) { nsView in
                        playerVM.setupPiP(playerLayer: nsView.avPlayerLayer)
                    }
                    .ignoresSafeArea()
                }

                if showControls || playerVM.error != nil {
                    VStack {
                        PlayerTopBar(
                            channel: channel,
                            programme: currentProgramme,
                            onBack: onBack,
                            onStop: { playerVM.stop(); onBack() }
                        )
                        Spacer()
                    }
                    .transition(.opacity)
                }

                if let prog = currentProgramme, let score = parseScore(prog.title) {
                    MatchScoreOverlay(programme: prog, score: score)
                }

                if let error = playerVM.error {
                    PlayerErrorOverlay(error: error, onRetry: { playerVM.retry() })
                }

                if showControls || playerVM.error != nil {
                    VStack {
                        Spacer()
                        PlayerControls(
                            pipController: playerVM.pipController,
                            showSidebar: $showSidebar
                        )
                    }
                    .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: showControls)
            .contentShape(Rectangle())
            .onTapGesture { showControlsTemporarily() }
            .onHover { if $0 { showControlsTemporarily() } }

            // Sidebar — hidden in fullscreen
            if showSidebar {
                PlayerSidebar(currentChannel: channel)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showSidebar)
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

    private func parseScore(_ title: String) -> (home: Int, away: Int)? {
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

// MARK: - Player Sidebar

struct PlayerSidebar: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(PlayerViewModel.self)   private var playerVM

    let currentChannel: Channel?

    enum SidebarTab { case onNow, schedule }
    @State private var tab: SidebarTab = .onNow

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                sidebarTab("On now",   tab: .onNow)
                sidebarTab("Schedule", tab: .schedule)
            }
            .background(Color.black.opacity(0.6))
            .overlay(alignment: .bottom) {
                Rectangle().fill(Color.white.opacity(0.07)).frame(height: 0.5)
            }

            switch tab {
            case .onNow:    PlayerOnNowTab(currentChannel: currentChannel)
            case .schedule: PlayerScheduleTab(channel: currentChannel)
            }
        }
        .frame(width: 230)
        .background(Color(hex: "0e0e0e"))
        .overlay(alignment: .leading) {
            Rectangle().fill(Color.white.opacity(0.07)).frame(width: 0.5)
        }
    }

    private func sidebarTab(_ label: String, tab: SidebarTab) -> some View {
        Button { self.tab = tab } label: {
            Text(label)
                .font(NS.Font.captionMed)
                .foregroundStyle(self.tab == tab ? Color.white.opacity(0.85) : Color.white.opacity(0.35))
                .frame(maxWidth: .infinity)
                .padding(.vertical, NS.Spacing.sm)
                .overlay(alignment: .bottom) {
                    if self.tab == tab {
                        Rectangle().fill(NS.accent).frame(height: 1.5)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - On Now Tab

struct PlayerOnNowTab: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(PlayerViewModel.self)   private var playerVM

    let currentChannel: Channel?

    private var sortedChannels: [Channel] {
        playlistVM.channels.sorted { a, b in
            let aPlaying = playerVM.currentChannel?.id == a.id
            let bPlaying = playerVM.currentChannel?.id == b.id
            if aPlaying != bPlaying { return aPlaying }
            let aLive = epgVM.currentProgramme(for: a) != nil
            let bLive = epgVM.currentProgramme(for: b) != nil
            if aLive != bLive { return aLive }
            return epgVM.nextProgramme(for: a) != nil && epgVM.nextProgramme(for: b) == nil
        }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                ForEach(sortedChannels) { channel in
                    PlayerSidebarRow(channel: channel)
                }
            }
            .padding(NS.Spacing.sm)
        }
    }
}

// MARK: - Schedule Tab

struct PlayerScheduleTab: View {

    @Environment(EPGViewModel.self)    private var epgVM
    @Environment(PlayerViewModel.self) private var playerVM

    let channel: Channel?

    private var programmes: [Programme] {
        guard let ch = channel else { return [] }
        return epgVM.schedule(for: ch, hours: 12).sorted { $0.start < $1.start }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                ForEach(programmes, id: \.start) { prog in
                    scheduleRow(prog)
                }
            }
            .padding(NS.Spacing.sm)
        }
    }

    private func scheduleRow(_ prog: Programme) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(prog.startTimeString)
                .font(NS.Font.monoSm)
                .foregroundStyle(prog.isNow ? NS.accent : Color.white.opacity(0.3))
            if prog.isNow {
                Text("Now playing")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(NS.accent)
                    .kerning(0.5)
            }
            Text(prog.title)
                .font(NS.Font.captionMed)
                .foregroundStyle(prog.isNow ? Color.white.opacity(0.85) : Color.white.opacity(0.4))
                .lineLimit(2)
            if prog.isNow {
                NSProgressBar(value: prog.progress, height: 2, glow: false).opacity(0.6)
            }
        }
        .padding(NS.Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(prog.isNow ? NS.accentGlow : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.md)
                .stroke(prog.isNow ? NS.accentBorder : Color.clear, lineWidth: 0.5)
        )
        .opacity(prog.stop < Date() ? 0.4 : 1.0)
    }
}

// MARK: - Top bar

struct PlayerTopBar: View {
    let channel: Channel?
    let programme: Programme?
    let onBack: () -> Void
    let onStop: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.1)))
            }
            .buttonStyle(.plain)
            .padding(.top, 2)

            VStack(alignment: .leading, spacing: 2) {
                Text(channel?.name ?? "")
                    .font(NS.Font.heading).foregroundStyle(.white)
                if let prog = programme {
                    Text(prog.title)
                        .font(NS.Font.caption)
                        .foregroundStyle(Color.white.opacity(0.5))
                }
            }

            Spacer()

            HStack(spacing: 6) {
                NSLiveBadge(isLive: programme?.isNow ?? false)
                NSIconButton(icon: "xmark") { onStop() }
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.top, 16)
        .padding(.bottom, 20)
        .background(NS.playerTopGradient)
    }
}

// MARK: - Match score overlay

struct MatchScoreOverlay: View {
    let programme: Programme
    let score: (home: Int, away: Int)

    private var teams: (home: String, away: String) {
        let parts = programme.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return ("Home", "Away") }
        return (
            parts[0].trimmingCharacters(in: .whitespaces),
            parts[1].components(separatedBy: " — ").first?.trimmingCharacters(in: .whitespaces) ?? parts[1]
        )
    }

    private var competition: String {
        guard let range = programme.title.range(of: " — ") else { return "" }
        return String(programme.title[range.upperBound...])
    }

    var body: some View {
        VStack(spacing: 20) {
            if !competition.isEmpty {
                Text(competition.uppercased())
                    .font(NS.Font.label).kerning(1.4)
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
        min(90, max(0, Int(Date().timeIntervalSince(programme.start) / 60)))
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
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.1)))
                Text(emoji).font(.system(size: 22))
            }
            .frame(width: 52, height: 52)
            Text(name)
                .font(NS.Font.cardTitle).foregroundStyle(.white)
                .multilineTextAlignment(.center).frame(maxWidth: 120)
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
                .font(NS.Font.scoreXL).foregroundStyle(.white)
                .shadow(color: NS.accent.opacity(0.3), radius: 20)
            Text("\(minute)'")
                .font(NS.Font.mono).foregroundStyle(NS.accent2)
                .padding(.horizontal, 10).padding(.vertical, 2)
                .background(NS.accentGlow)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(NS.accentBorder))
        }
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
                .background(isPrimary ? NS.accent : isHovered ? Color.white.opacity(0.12) : Color.white.opacity(0.07))
                .clipShape(RoundedRectangle(cornerRadius: isPrimary ? 11 : 9))
                .overlay(RoundedRectangle(cornerRadius: isPrimary ? 11 : 9)
                    .stroke(Color.white.opacity(isPrimary ? 0 : 0.08)))
                .shadow(color: isPrimary ? NS.accent.opacity(0.5) : .clear, radius: 10)
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

struct PlayerProgressBar: View {
    @Environment(PlayerViewModel.self) private var playerVM
    @State private var isHovering = false
    let timer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color.white.opacity(0.1))
                .frame(height: isHovering ? 4 : 3)
            RoundedRectangle(cornerRadius: 2)
                .fill(NS.accent)
                .frame(width: .infinity * 0.38, height: isHovering ? 4 : 3)
                .shadow(color: NS.accent.opacity(0.6), radius: 4)
        }
        .frame(height: 4)
        .animation(.easeOut(duration: 0.12), value: isHovering)
        .onHover { isHovering = $0 }
        .onReceive(timer) { _ in }
    }
}

struct PlayerErrorOverlay: View {
    let error: PlayerError
    let onRetry: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.75)
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 36)).foregroundStyle(NS.amber)
                Text(error.errorDescription ?? "Stream unavailable")
                    .font(NS.Font.bodyMedium).foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                if let suggestion = error.recoverySuggestion {
                    Text(suggestion)
                        .font(NS.Font.caption)
                        .foregroundStyle(Color.white.opacity(0.5))
                        .multilineTextAlignment(.center)
                }
                Button("Retry", action: onRetry)
                    .buttonStyle(.borderedProminent).tint(NS.amber)
            }
            .padding(32)
        }
        .ignoresSafeArea()
    }
}
