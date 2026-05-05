// PlayerControlsOverlay.swift — NS-054
// Floating controls: play/pause, quality selector, PiP, AirPlay.

import SwiftUI
import AVKit
import AVFoundation

struct PlayerControlsOverlay: View {

    @Environment(PlayerViewModel.self) private var playerVM
    var pipController: AVPictureInPictureController?

    var body: some View {
        HStack(spacing: 16) {

            // Play / Pause
            Button {
                playerVM.togglePlayback()
            } label: {
                Image(systemName: playerVM.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.space, modifiers: [])

            Spacer()

            // Quality selector
            Menu {
                ForEach(StreamQuality.presets, id: \.displayName) { q in
                    Button(q.displayName) { playerVM.setQuality(q) }
                }
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "dial.medium")
                    Text(playerVM.quality.displayName)
                        .font(.system(size: 12, weight: .medium))
                }
                .foregroundStyle(.white)
            }
            .menuStyle(.borderlessButton)
            .fixedSize()

            // AirPlay (NS-045)
            AVRoutePickerViewRepresentable()
                .frame(width: 24, height: 24)

            // PiP (NS-044)
            if let pip = pipController {
                Button {
                    pip.startPictureInPicture()
                } label: {
                    Image(systemName: "rectangle.inset.bottomright.filled")
                        .font(.system(size: 18))
                        .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                .disabled(!AVPictureInPictureController.isPictureInPictureSupported())
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.black.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - AirPlay picker wrapper (NS-045)

struct AVRoutePickerViewRepresentable: NSViewRepresentable {
    func makeNSView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        return picker
    }
    func updateNSView(_ nsView: AVRoutePickerView, context: Context) {}
}
