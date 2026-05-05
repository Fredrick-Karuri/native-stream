// PlayerView.swift — NS-053
// Main player view: video, controls overlay, now-playing banner, error overlay.

import SwiftUI
import AVFoundation
import AVKit

struct PlayerView: View {

    @Environment(PlayerViewModel.self)  private var playerVM
    @Environment(EPGViewModel.self)     private var epgVM

    let channel: Channel?

    @State private var showControls = true
    @State private var controlsTimer: Timer? = nil
    @State private var pipController: AVPictureInPictureController? = nil
    @State private var playerViewRef: AVPlayerNSView? = nil

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if channel == nil {
                noSelectionView
            } else {
                // Video
                AVPlayerRepresentableWithRef(player: playerVM.player) { nsView in
                    playerViewRef = nsView
                    setupPiP(nsView: nsView)
                }
                .ignoresSafeArea()
                .onHover { hovering in
                    if hovering { showControlsTemporarily() }
                }

                // Controls overlay
                if showControls {
                    VStack {
                        Spacer()
                        PlayerControlsOverlay(pipController: pipController)
                            .transition(.opacity)
                    }
                    .padding()
                }

                // Now playing banner
                if let channel {
                    VStack {
                        NowPlayingBanner(channel: channel)
                        Spacer()
                    }
                }

                // Error overlay
                if let error = playerVM.error {
                    errorOverlay(error: error)
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showControls)
        .animation(.easeInOut(duration: 0.2), value: playerVM.error == nil)
        .onTapGesture { showControlsTemporarily() }
        .onDisappear {playerVM.cleanup()}
        .onReceive(NotificationCenter.default.publisher(for: .init("enterPiP"))) { _ in
            pipController?.startPictureInPicture()
        }
    }

    // MARK: - Subviews

    private var noSelectionView: some View {
        VStack(spacing: 12) {
            Image(systemName: "play.tv")
                .font(.system(size: 56))
                .foregroundStyle(.white.opacity(0.3))
            Text("Select a channel to watch")
                .font(.title3)
                .foregroundStyle(.white.opacity(0.5))
        }
    }

    private func errorOverlay(error: PlayerError) -> some View {
        ZStack {
            Color.black.opacity(0.75)
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 36))
                    .foregroundStyle(.orange)
                Text(error.errorDescription ?? "Stream unavailable")
                    .font(.headline)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                if let suggestion = error.recoverySuggestion {
                    Text(suggestion)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                Button("Retry") { playerVM.retry() }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
            }
            .padding(32)
        }
        .ignoresSafeArea()
    }

    // MARK: - Controls auto-hide

    private func showControlsTemporarily() {
        withAnimation { showControls = true }
        controlsTimer?.invalidate()
        controlsTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { _ in
            withAnimation { showControls = false }
        }
    }

    // MARK: - PiP setup (NS-044)

    private func setupPiP(nsView: AVPlayerNSView) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        pipController = AVPictureInPictureController(playerLayer: nsView.avPlayerLayer)
    }
}

// MARK: - AVPlayerRepresentable with callback for NSView reference

struct AVPlayerRepresentableWithRef: NSViewRepresentable {
    let player: AVPlayer?
    let onViewCreated: (AVPlayerNSView) -> Void

    func makeNSView(context: Context) -> AVPlayerNSView {
        let view = AVPlayerNSView()
        view.player = player
        onViewCreated(view)
        return view
    }

    func updateNSView(_ nsView: AVPlayerNSView, context: Context) {
        if nsView.player !== player { nsView.player = player }
    }
}