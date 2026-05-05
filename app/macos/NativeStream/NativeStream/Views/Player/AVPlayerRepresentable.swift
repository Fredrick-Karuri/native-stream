// AVPlayerRepresentable.swift — NS-043
// NSViewRepresentable wrapping AVPlayerLayer for SwiftUI embedding.

import SwiftUI
import AVFoundation
import AVKit

struct AVPlayerRepresentable: NSViewRepresentable {

    let player: AVPlayer?

    func makeNSView(context: Context) -> AVPlayerNSView {
        let view = AVPlayerNSView()
        view.player = player
        return view
    }

    func updateNSView(_ nsView: AVPlayerNSView, context: Context) {
        if nsView.player !== player {
            nsView.player = player
        }
    }
}

// MARK: - Custom NSView with AVPlayerLayer

final class AVPlayerNSView: NSView {

    var player: AVPlayer? {
        get { playerLayer.player }
        set { playerLayer.player = newValue }
    }

    private var playerLayer: AVPlayerLayer {
        layer as! AVPlayerLayer
    }

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        let layer = AVPlayerLayer()
        layer.videoGravity = .resizeAspect
        layer.backgroundColor = NSColor.black.cgColor
        self.layer = layer
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not used")
    }

    override func layout() {
        super.layout()
        playerLayer.frame = bounds
    }

    // Expose the AVPlayerLayer for PiP controller
    var avPlayerLayer: AVPlayerLayer { playerLayer }
}