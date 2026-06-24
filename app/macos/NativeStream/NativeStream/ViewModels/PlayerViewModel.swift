// PlayerViewModel
// Owns AVPlayer lifecycle, retry logic, quality selection, and platform integrations.

import Foundation
import AVFoundation
import MediaPlayer
import Observation
import Combine
import AVKit
import IOKit.pwr_mgt

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
    var channelList: [Channel] = []


    var pipController: AVPictureInPictureController?
    var pipActive: Bool = false

    // MARK: - Dependencies

    var epgViewModel: EPGViewModel? = nil
    var bufferPreset: BufferPreset = .balanced

    private var playerItemObservation: Task<Void, Never>?
    private let sleepAssertion = SleepAssertion()

    private let maxRetries = 3
    private let retryDelay: TimeInterval = 2

    // MARK: - Playback

    func play(channel: Channel) async throws {
        playerItemObservation?.cancel()
        error = nil
        retryCount = 0
        currentChannel = channel
        startPlayback(url: channel.streamURL)
    }

    /// Play any URL directly without persisting a channel.
    /// Creates a temporary Channel not added to the playlist.
    func playURL(_ urlString: String, headers: [String: String] = [:]) {
        guard let url = URL(string: urlString.trimmingCharacters(in: .whitespaces)) else {
            error = .unsupportedFormat
            return
        }
        let temp = Channel(
            tvgId: "",
            name: urlString,
            groupTitle: "Direct",
            streamURL: url,
            streamHeaders: headers
        )
        currentChannel = temp
        error = nil
        retryCount = 0
        startPlayback(url: url, headers: headers)
    }

    // MARK: - Internal playback

    /// Uses AVURLAsset when headers are present so streams requiring
    /// Referer/User-Agent or custom tokens play without buffering failures.
    private func startPlayback(url: URL, headers: [String: String] = [:]) {
        print("[player] url=\(url) headers=\(headers)")
        let item: AVPlayerItem
 
        if headers.isEmpty {
            item = AVPlayerItem(url: url)
        } else {
            // AVURLAssetHTTPHeaderFieldsKey injects headers on every HLS request
            let asset = AVURLAsset(
                url: url,
                options: ["AVURLAssetHTTPHeaderFieldsKey": headers]
            )
            item = AVPlayerItem(asset: asset)
        }
 
        item.preferredForwardBufferDuration = 0
        item.automaticallyPreservesTimeOffsetFromLive = true
 
        player?.pause()
        player = AVPlayer(playerItem: item)
        player?.automaticallyWaitsToMinimizeStalling = true
        player?.play()
        isPlaying = true
        manageScreenSleep(disableSleep: true)
 
        observePlayerItem(item)
        setupNowPlaying()

    }


    // MARK: Retry logic via async KVO observation
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
                            print("[player] error=\(item.error?.localizedDescription ?? "unknown")")
                            print("[player] error detail=\(String(describing: item.error))")
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
            manageScreenSleep(disableSleep: false)
        } else {
            player.play()
            isPlaying = true
            updateNowPlayingState(paused: false)
            manageScreenSleep(disableSleep: true)
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
    private func manageScreenSleep(disableSleep: Bool) {
        if disableSleep {
            guard sleepAssertion.id == 0 else { return }
            let result = IOPMAssertionCreateWithName(
                kIOPMAssertionTypeNoDisplaySleep as CFString,
                IOPMAssertionLevel(kIOPMAssertionLevelOn),
                "NativeStream playback" as CFString,
                &sleepAssertion.id
            )
            if result != kIOReturnSuccess { sleepAssertion.id = 0 }
        } else {
            guard sleepAssertion.id != 0 else { return }
            IOPMAssertionRelease(sleepAssertion.id)
            sleepAssertion.id = 0
        }
    }

    deinit {
        if sleepAssertion.id != 0 { IOPMAssertionRelease(sleepAssertion.id) }
    }

    // MARK: - Cleanup

    func cleanup() {
        pipController?.stopPictureInPicture()
        pipController = nil
        playerItemObservation?.cancel()
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        isPlaying = false
        pipActive = false
        manageScreenSleep(disableSleep: false)
    }

    func stop() {
        cleanup()
        currentChannel = nil
    }

    func toggleMute() {
        isMuted.toggle()
        player?.isMuted = isMuted
    }

    func playNext(in channels: [Channel]) {
        guard let current = currentChannel,
              let idx = channels.firstIndex(where: { $0.id == current.id }) else { return }
        let next = channels[(idx + 1) % channels.count]
        Task { try? await play(channel: next) }
    }

    func playPrevious(in channels: [Channel]) {
        guard let current = currentChannel,
              let idx = channels.firstIndex(where: { $0.id == current.id }) else { return }
        let prev = channels[(idx - 1 + channels.count) % channels.count]
        Task { try? await play(channel: prev) }
    }
}

extension PlayerViewModel: AVPictureInPictureControllerDelegate {
    nonisolated func pictureInPictureWillStart(_ controller: AVPictureInPictureController) {
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

private final class SleepAssertion: @unchecked Sendable {
    var id: IOPMAssertionID = 0
}

