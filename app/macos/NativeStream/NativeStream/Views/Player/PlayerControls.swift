import SwiftUI
import AVFoundation
import AVKit
import Combine

// MARK: - Player controls

struct PlayerControls: View {
    @Environment(PlayerViewModel.self) private var playerVM
    var pipController: AVPictureInPictureController?
    @Binding var showSidebar: Bool

    var body: some View {
        VStack(spacing: 12) {
            PlayerProgressBar()
            HStack(spacing: 10) {
                CtrlButton(icon: "backward.end.fill", size: 14) { }
                CtrlButton(
                    icon: playerVM.isPlaying ? "pause.fill" : "play.fill",
                    size: 16, isPrimary: true
                ) { playerVM.togglePlayback() }
                CtrlButton(icon: "forward.end.fill", size: 14) { }

                Spacer()

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
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.08)))
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

                if let pip = pipController {
                    CtrlButton(icon: "rectangle.inset.bottomright.filled", size: 14) {
                        pip.startPictureInPicture()
                    }
                }

                NSIconButton(
                    icon: playerVM.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                    size: 14
                ) { playerVM.toggleMute() }

                AVRoutePickerViewRepresentable()
                    .frame(width: 36, height: 36)

                // Sidebar / fullscreen toggle
                NSIconButton(
                    icon: showSidebar
                        ? "arrow.up.left.and.arrow.down.right"
                        : "arrow.down.right.and.arrow.up.left",
                    size: 14
                ) {
                    withAnimation(.easeInOut(duration: 0.2)) { showSidebar.toggle() }
                }
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.top, 32)
        .padding(.bottom, 20)
        .background(NS.playerBottomGradient)
    }
}
