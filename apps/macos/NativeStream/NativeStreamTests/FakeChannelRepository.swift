// FakeChannelRepository
// Controllable test double for ChannelRepository — returns a preset
// ChannelFetchResult instead of hitting the network/parser.

import Foundation
@testable import NativeStream

actor FakeChannelRepository: ChannelRepository {

    var resultToReturn = ChannelFetchResult(channels: [], perSource: [])
    private(set) var fetchChannelsCallCount = 0
    private(set) var lastRequestedSources: [PlaylistSource] = []

    func fetchChannels(from sources: [PlaylistSource]) async -> ChannelFetchResult {
        fetchChannelsCallCount += 1
        lastRequestedSources = sources
        return resultToReturn
    }

    /// Test setup helper.
    func setResult(_ result: ChannelFetchResult) {
        resultToReturn = result
    }
}
