// File: ChannelNavigation.swift
//
// Pure channel-list navigation logic extracted from MediaKeyHandler. Given the
// current channel (if any) and the full channel list, decides which channel
// "next"/"previous" should select, with wraparound

import Foundation

enum ChannelNavigation {

    /// Returns the channel that should play next, wrapping to the start of the list.
    /// Returns nil if `channels` is empty. If `currentChannel` is nil, or isn't
    /// found in `channels` (e.g. it was removed from the playlist), returns the
    /// first channel — matching the original MediaKeyHandler behavior exactly.
    static func nextChannel(after currentChannel: Channel?, in channels: [Channel]) -> Channel? {
        guard !channels.isEmpty else { return nil }
        guard let current = currentChannel,
              let index = channels.firstIndex(of: current) else {
            return channels[0]
        }
        return channels[(index + 1) % channels.count]
    }

    /// Returns the channel that should play previously, wrapping to the end of the list.
    /// Returns nil if `channels` is empty. If `currentChannel` is nil, or isn't
    /// found in `channels` (e.g. it was removed from the playlist), returns the
    /// last channel — matching the original MediaKeyHandler behavior exactly.
    static func previousChannel(before currentChannel: Channel?, in channels: [Channel]) -> Channel? {
        guard !channels.isEmpty else { return nil }
        guard let current = currentChannel,
              let index = channels.firstIndex(of: current) else {
            return channels[channels.count - 1]
        }
        return channels[(index - 1 + channels.count) % channels.count]
    }
}
