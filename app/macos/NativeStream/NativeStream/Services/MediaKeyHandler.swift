// MediaKeyHandler.swift — NS-313
// Wires macOS media keys and Control Center remote commands to the player.

import Foundation
import MediaPlayer

@MainActor
final class MediaKeyHandler {

    private weak var playerVM: PlayerViewModel?
    private weak var playlistVM: PlaylistViewModel?

    func configure(playerVM: PlayerViewModel, playlistVM: PlaylistViewModel) {
        self.playerVM = playerVM
        self.playlistVM = playlistVM

        let cc = MPRemoteCommandCenter.shared()

        cc.playCommand.isEnabled = true
        cc.playCommand.addTarget { [weak self] _ in
            guard let self, let vm = self.playerVM else { return .commandFailed }
            if !vm.isPlaying { vm.togglePlayback() }
            return .success
        }

        cc.pauseCommand.isEnabled = true
        cc.pauseCommand.addTarget { [weak self] _ in
            guard let self, let vm = self.playerVM else { return .commandFailed }
            if vm.isPlaying { vm.togglePlayback() }
            return .success
        }

        cc.togglePlayPauseCommand.isEnabled = true
        cc.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.playerVM?.togglePlayback()
            return .success
        }

        // Next/previous channel
        cc.nextTrackCommand.isEnabled = true
        cc.nextTrackCommand.addTarget { [weak self] _ in
            self?.playNextChannel()
            return .success
        }

        cc.previousTrackCommand.isEnabled = true
        cc.previousTrackCommand.addTarget { [weak self] _ in
            self?.playPreviousChannel()
            return .success
        }
    }

    private func playNextChannel() {
        guard let playerVM, let playlistVM else { return }
        let channels = playlistVM.channels
        guard !channels.isEmpty else { return }
        if let current = playerVM.currentChannel,
           let idx = channels.firstIndex(of: current) {
            let next = channels[(idx + 1) % channels.count]
            playerVM.play(channel: next)
        } else {
            playerVM.play(channel: channels[0])
        }
    }

    private func playPreviousChannel() {
        guard let playerVM, let playlistVM else { return }
        let channels = playlistVM.channels
        guard !channels.isEmpty else { return }
        if let current = playerVM.currentChannel,
           let idx = channels.firstIndex(of: current) {
            let prev = channels[(idx - 1 + channels.count) % channels.count]
            playerVM.play(channel: prev)
        } else {
            playerVM.play(channel: channels[channels.count - 1])
        }
    }
}