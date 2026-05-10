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
        VStack(spacing: NS.Spacing.md) {
            PlayerProgressBar()
            HStack(spacing: NS.Spacing.md) {
                CtrlButton(icon: "backward.end.fill", size: NS.Player.ctrlIconSm) { }
                CtrlButton(
                    icon: playerVM.isPlaying ? "pause.fill" : "play.fill",
                    size: NS.Player.ctrlIconLg, isPrimary: true
                ) { playerVM.togglePlayback() }
                CtrlButton(icon: "forward.end.fill", size: NS.Player.ctrlIconSm) { }

                Spacer()

                Menu {
                    ForEach(StreamQuality.presets, id: \.displayName) { q in
                        Button(q.displayName) { playerVM.setQuality(q) }
                    }
                } label: {
                    Text(playerVM.quality.displayName)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(Color.white.opacity(0.6))
                        .padding(.horizontal, NS.Chip.paddingH)
                        .frame(height: NS.Player.menuHeight)
                        .background(Color.white.opacity(0.07))
                        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                        .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(Color.white.opacity(0.08)))
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

                if let pip = pipController {
                    CtrlButton(icon: "rectangle.inset.bottomright.filled", size: NS.Player.ctrlIconSm) {
                        pip.startPictureInPicture()
                    }
                }

                NSIconButton(
                    icon: playerVM.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                    size: NS.Player.ctrlIconSm
                ) { playerVM.toggleMute() }

                AVRoutePickerViewRepresentable()
                    .frame(width: NS.IconButton.sizeLg, height: NS.IconButton.sizeLg)

                // Sidebar / fullscreen toggle
                NSIconButton(
                    icon: showSidebar
                        ? "arrow.up.left.and.arrow.down.right"
                        : "arrow.down.right.and.arrow.up.left",
                    size: NS.Player.ctrlIconSm
                ) {
                    withAnimation(.easeInOut(duration: 0.2)) { showSidebar.toggle() }
                }
            }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.top, NS.Player.errorPadding)
        .padding(.bottom, NS.Spacing.xl)
        .background(NS.playerBottomGradient)
    }
}


struct AVRoutePickerViewRepresentable: NSViewRepresentable {
    func makeNSView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        return picker
    }
    func updateNSView(_ nsView: AVRoutePickerView, context: Context) {}
}
