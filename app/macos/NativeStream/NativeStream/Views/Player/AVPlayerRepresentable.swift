// AVPlayerRepresentable.swift — NS-043
import SwiftUI
import AVFoundation
import AVKit

struct AVPlayerRepresentableRef: NSViewRepresentable {
    let player: AVPlayer?
    let onViewCreated: (AVPlayerNSView) -> Void

    func makeNSView(context: Context) -> AVPlayerNSView {
        let v = AVPlayerNSView()
        v.player = player
        onViewCreated(v)
        return v
    }

    func updateNSView(_ nsView: AVPlayerNSView, context: Context) {
        if nsView.player !== player { nsView.player = player }
    }
}

// Keep original for any non-PiP usage
struct AVPlayerRepresentable: NSViewRepresentable {
    let player: AVPlayer?

    func makeNSView(context: Context) -> AVPlayerNSView {
        let v = AVPlayerNSView()
        v.player = player
        return v
    }

    func updateNSView(_ nsView: AVPlayerNSView, context: Context) {
        if nsView.player !== player { nsView.player = player }
    }
}

// MARK: - NSView

final class AVPlayerNSView: NSView {

    var player: AVPlayer? {
        get { avPlayerLayer.player }
        set { avPlayerLayer.player = newValue }
    }

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        avPlayerLayer.videoGravity = .resizeAspect
        avPlayerLayer.backgroundColor = NSColor.black.cgColor
        layer?.addSublayer(avPlayerLayer)
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layout() {
        super.layout()
        avPlayerLayer.frame = bounds
    }

    let avPlayerLayer = AVPlayerLayer()
}