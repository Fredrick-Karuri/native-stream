// NS-041 + NS-042: PlayerViewModel
// Owns AVPlayer lifecycle, retry logic, quality selection, and platform integrations.

import Foundation
import AVFoundation
import MediaPlayer
import Observation
import Combine
import AVKit

@Observable
@MainActor
final class PlayerViewModel:NSObject {

    // MARK: - State

    var currentChannel: Channel? = nil
    var player: AVPlayer? = nil
    var isPlaying: Bool = false
    var quality: StreamQuality = .auto
    var error: PlayerError? = nil
    private(set) var retryCount: Int = 0
    var isMuted: Bool = false


    var pipController: AVPictureInPictureController?
    var pipActive: Bool = false

    // MARK: - Dependencies

    var epgViewModel: EPGViewModel? = nil
    var bufferPreset: BufferPreset = .balanced

    private var playerItemObservation: Task<Void, Never>?
    private let maxRetries = 3
    private let retryDelay: TimeInterval = 2
    private var liveEdgeTimer: Timer? = nil

    // MARK: - Playback

    func play(channel: Channel) async throws {
        playerItemObservation?.cancel()
        error = nil
        retryCount = 0
        currentChannel = channel

        if let detail = try? await APIClient.shared.getChannel(id: channel.id),
           let activeURL = detail.activeLink.flatMap({ URL(string: $0.url) }) {
            startPlayback(url: activeURL)
        } else {
            // Fall back to the URL embedded in the channel model
            startPlayback(url: channel.streamURL)
        }
    }

private func startPlayback(url: URL) {
    let item = AVPlayerItem(url: url)
    item.preferredForwardBufferDuration = 0 // Let AVPlayer handle native buffering smoothly
    item.automaticallyPreservesTimeOffsetFromLive = true

    player?.pause()
    player = AVPlayer(playerItem: item)
    player?.automaticallyWaitsToMinimizeStalling = true
    player?.play()
    isPlaying = true
}


    // MARK: - NS-042: Retry logic via async KVO observation
    private func observePlayerItem(_ item: AVPlayerItem) {
        playerItemObservation?.cancel()
        playerItemObservation = Task { [weak self] in
            guard let self else { return }

            await withTaskGroup(of: Void.self) { group in
                // Status observation (existing)
                group.addTask {
                    for await status in item.publisher(for: \.status).values {
                        guard !Task.isCancelled else { return }
                        if status == .failed {
                            await self.handleFailure(underlyingError: item.error)
                            return
                        }
                    }
                }

                // Stall observation (new)
                group.addTask {
                    for await _ in NotificationCenter.default
                        .notifications(named: .AVPlayerItemPlaybackStalled, object: item) {
                        guard !Task.isCancelled else { return }
                        await MainActor.run { self.player?.play() }  // attempt resume
                    }
                }
            }
        }
    }

    private func handleFailure(underlyingError: Error?) async {
        guard retryCount < maxRetries else {
            error = .maxRetriesExceeded
            updateNowPlayingState(paused: true)
            return
        }

        retryCount += 1
        try? await Task.sleep(for: .seconds(retryDelay))
        guard !Task.isCancelled, let channel = currentChannel else { return }

        // Re-fetch — server may have a fresher active link after a probe
        guard let detail = try? await APIClient.shared.getChannel(id: channel.id),
              let activeURL = detail.activeLink.flatMap({ URL(string: $0.url) }) else {
            error = .noActiveLink
            return
        }
        startPlayback(url: activeURL)
    }

    // MARK: - Retry (manual, from UI)

    func retry() {
        error = nil
        retryCount = 0
        guard let channel = currentChannel else { return }
        Task { try? await play(channel: channel) }
    }

    // PiP

    func setupPiP(playerLayer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported(),
              pipController == nil else { return }
        let pip = AVPictureInPictureController(playerLayer: playerLayer)
        pip?.delegate = self
        pipController = pip
    }

    func enterPiP() {
        pipController?.startPictureInPicture()
    }

    // MARK: - Toggle

    func togglePlayback() {
        guard let player else { return }
        if isPlaying {
            player.pause()
            isPlaying = false
            updateNowPlayingState(paused: true)
        } else {
            player.play()
            isPlaying = true
            updateNowPlayingState(paused: false)
        }
    }

    // MARK: - Quality (NS-054)

    func setQuality(_ q: StreamQuality) {
        quality = q
        // AVFoundation adaptive bitrate: restrict via preferredPeakBitRate
        switch q {
        case .auto:
            player?.currentItem?.preferredPeakBitRate = 0    // unlimited
        case .locked(let height):
            // Approximate bitrate caps per resolution
            let bitrate: Double = switch height {
            case 1080: 8_000_000
            case 720:  4_000_000
            case 480:  1_500_000
            default:   0
            }
            player?.currentItem?.preferredPeakBitRate = bitrate
        }
    }

    // MARK: - Now Playing (NS-046)

    func setupNowPlaying() {
        guard let channel = currentChannel else { return }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: channel.name,
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0,
        ]

        if let programme = epgViewModel?.currentProgramme(for: channel) {
            info[MPMediaItemPropertyArtist] = programme.title
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.togglePlayback()
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.togglePlayback()
            return .success
        }
    }

    private func updateNowPlayingState(paused: Bool) {
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        info[MPNowPlayingInfoPropertyPlaybackRate] = paused ? 0.0 : 1.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func setMainLayerHidden(_ hidden: Bool) {
        // AVPictureInPictureController handles its own rendering;
        // we just need to hide the source layer
        pipController?.playerLayer.opacity = hidden ? 0 : 1
    }

    private func startLiveEdgeRefresh() {
        liveEdgeTimer?.invalidate()
        liveEdgeTimer = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.isPlaying, self.error == nil else { return }
                self.player?.seek(to: .positiveInfinity)
            }
        }
    }

    private func stopLiveEdgeRefresh() {
        liveEdgeTimer?.invalidate()
        liveEdgeTimer = nil
    }

    // MARK: - Cleanup

    func cleanup() {
        stopLiveEdgeRefresh()
        pipController?.stopPictureInPicture()   // ← new
        pipController = nil                      // ← new
        playerItemObservation?.cancel()
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        isPlaying = false
        pipActive = false                        // ← new
    }

    func stop() {
        cleanup()
        currentChannel = nil
    }

    func toggleMute() {
        isMuted.toggle()
        player?.isMuted = isMuted
    }
}

extension PlayerViewModel: AVPictureInPictureControllerDelegate {
    nonisolated func pictureInPictureWillStart(_ controller: AVPictureInPictureController) {
        print("🟢 PiP will start — delegate fired")
        Task { @MainActor in
            self.pipActive = true
            self.setMainLayerHidden(true)
        }
    }
    nonisolated func pictureInPictureWillStop(_ controller: AVPictureInPictureController) {
        Task { @MainActor in
            self.pipActive = false
            self.setMainLayerHidden(false)
        }
    }
}


