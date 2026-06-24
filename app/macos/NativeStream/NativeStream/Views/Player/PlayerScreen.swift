// PlayerScreen.swift

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
    @Binding var isSidebarOpen: Bool

    @State private var hideTask: Task<Void, Never>? = nil
    
    private var activeChannel: Channel? {
        playerVM.currentChannel ?? channel
    }

    private var currentProgramme: Programme? {
        guard let ch = activeChannel else { return nil }
        return epgVM.currentProgramme(for: ch)
    }

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
                            channel: activeChannel,
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
                            currentProgramme: currentProgramme,
                            showSidebar: $isSidebarOpen,
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
            if isSidebarOpen {
                PlayerSidebar(currentChannel: activeChannel)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isSidebarOpen)
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
