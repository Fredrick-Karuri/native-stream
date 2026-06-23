// Controls.swift

import SwiftUI
import AVFoundation
import AVKit
import Combine

// MARK: - Player controls

struct PlayerControls: View {
    @Environment(PlayerViewModel.self) private var playerVM
    var pipController: AVPictureInPictureController?
    var currentProgramme: Programme?
    
    @Binding var showSidebar: Bool

    var body: some View {
        VStack(spacing: NS.Spacing.md) {
            NSProgressBar(value: currentProgramme?.progress ?? 0, height: 3, glow: true)
            HStack(spacing: NS.Spacing.md) {
                CtrlButton(icon: "backward.end.fill", size: NS.Player.ctrlIconSm) {
                    playerVM.playPrevious(in: playerVM.channelList)
                }
                CtrlButton(
                    icon: playerVM.isPlaying ? "pause.fill" : "play.fill",
                    size: NS.Player.ctrlIconLg, isPrimary: true
                ) { playerVM.togglePlayback() }
                CtrlButton(icon: "forward.end.fill", size: NS.Player.ctrlIconSm) {
                    playerVM.playNext(in: playerVM.channelList)
                }


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
                .frame(width: isPrimary ? NS.Player.ctrlPrimary : NS.Player.ctrlSecondary,
                        height: isPrimary ? NS.Player.ctrlPrimary : NS.Player.ctrlSecondary)
                .background(isPrimary ? NS.accent : isHovered ? Color.white.opacity(0.12) : Color.white.opacity(0.07))
                .clipShape(RoundedRectangle(cornerRadius: isPrimary ? NS.Player.ctrlRadiusPrimary : NS.Player.ctrlRadiusSecondary))
                .overlay(RoundedRectangle(cornerRadius: isPrimary ? NS.Player.ctrlRadiusPrimary : NS.Player.ctrlRadiusSecondary)
                    .stroke(Color.white.opacity(isPrimary ? 0 : 0.08)))
                .shadow(color: isPrimary ? NS.accent.opacity(0.5) : .clear, radius: 10)
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}
