// NowPlayingService
// Manages macOS Now Playing integration (Control Center, media keys).

import Foundation
import MediaPlayer
import AVFoundation

final class NowPlayingService {

    static let shared = NowPlayingService()
    private init() {}

    func configure() {
        let commandCenter = MPRemoteCommandCenter.shared()
        // Targets are set by PlayerViewModel — just enable the commands here
        commandCenter.playCommand.isEnabled = true
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.nextTrackCommand.isEnabled = false   // N/A for live TV
        commandCenter.previousTrackCommand.isEnabled = false
    }

    func update(channel: Channel, programme: Programme?) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: channel.name,
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0,
            MPMediaItemPropertyMediaType: MPMediaType.anyVideo.rawValue,
        ]
        if let programme {
            info[MPMediaItemPropertyArtist] = programme.title
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func clear() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
