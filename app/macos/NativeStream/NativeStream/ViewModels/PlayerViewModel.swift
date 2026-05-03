// NS-041 + NS-042: PlayerViewModel
// Owns AVPlayer lifecycle, retry logic, quality selection, and platform integrations.

import Foundation
import AVFoundation
import MediaPlayer
import Observation
import Combine

@Observable
@MainActor
final class PlayerViewModel {

    // MARK: - State

    var currentChannel: Channel? = nil
    var player: AVPlayer? = nil
    var isPlaying: Bool = false
    var quality: StreamQuality = .auto
    var error: PlayerError? = nil
    private(set) var retryCount: Int = 0

    // MARK: - Dependencies

    var epgViewModel: EPGViewModel? = nil
    var bufferPreset: BufferPreset = .balanced

    private var playerItemObservation: Task<Void, Never>?
    private let maxRetries = 3
    private let retryDelay: TimeInterval = 2

    // MARK: - Playback

    func play(channel: Channel) {
        // Cancel any in-flight retry observation
        playerItemObservation?.cancel()
        error = nil
        retryCount = 0
        currentChannel = channel
        startPlayback(url: channel.streamURL)
    }

    private func startPlayback(url: URL) {
        let item = AVPlayerItem(url: url)
        item.preferredForwardBufferDuration = bufferPreset.seconds

        if player == nil {
            player = AVPlayer(playerItem: item)
            player?.automaticallyWaitsToMinimizeStalling = true
        } else {
            player?.replaceCurrentItem(with: item)
        }

        player?.play()
        observePlayerItem(item)
        setupNowPlaying()
    }

    // MARK: - NS-042: Retry logic via async KVO observation

    private func observePlayerItem(_ item: AVPlayerItem) {
        playerItemObservation?.cancel()
        playerItemObservation = Task { [weak self] in
            guard let self else { return }

            // Poll status — Swift concurrency KVO bridge
            for await status in item.publisher(for: \.status).values {
                guard !Task.isCancelled else { return }
                switch status {
                case .readyToPlay:
                    await MainActor.run { self.isPlaying = true }

                case .failed:
                    let err = item.error
                    await MainActor.run {
                        self.isPlaying = false
                        Task { await self.handleFailure(underlyingError: err) }
                    }
                    return

                case .unknown:
                    break

                @unknown default:
                    break
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
        guard !Task.isCancelled, let url = currentChannel?.streamURL else { return }
        startPlayback(url: url)
    }

    // MARK: - Retry (manual, from UI)

    func retry() {
        error = nil
        retryCount = 0
        guard let channel = currentChannel else { return }
        play(channel: channel)
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

    // MARK: - Cleanup

    func cleanup() {
        playerItemObservation?.cancel()
    }
}
