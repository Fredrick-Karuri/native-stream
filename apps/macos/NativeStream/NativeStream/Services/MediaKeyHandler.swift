// MediaKeyHandler.swift
// Wires macOS media keys and Control Center remote commands to the player.

import Foundation
import MediaPlayer

@MainActor
final class MediaKeyHandler {

    private weak var playerVM: PlayerViewModel?
    private weak var channelLoadingVM: ChannelLoadingViewModel?

    func configure(playerVM: PlayerViewModel, channelLoadingVM: ChannelLoadingViewModel) {
        self.playerVM = playerVM
        self.channelLoadingVM = channelLoadingVM

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
        guard let playerVM, let channelLoadingVM,
              let next = ChannelNavigation.nextChannel(after: playerVM.currentChannel, in: channelLoadingVM.channels)
        else { return }
        Task { try? await playerVM.play(channel: next) }
    }

    private func playPreviousChannel() {
        guard let playerVM, let channelLoadingVM,
              let prev = ChannelNavigation.previousChannel(before: playerVM.currentChannel, in: channelLoadingVM.channels)
        else { return }
        Task { try? await playerVM.play(channel: prev) }
    }
}
