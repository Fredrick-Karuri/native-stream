// FakeSettingsPersisting
// In-memory test double for SettingsPersisting — no real disk I/O. Shared
// across SourceViewModelTests and ChannelLoadingViewModelTests.

import Foundation
@testable import NativeStream

actor FakeSettingsPersisting: SettingsPersisting {

    private(set) var storedSources: [PlaylistSource] = []
    private(set) var storedChannels: [Channel] = []
    private(set) var saveSourcesCallCount = 0
    private(set) var saveChannelsCallCount = 0
    private(set) var deleteAllCallCount = 0

    func loadSources() async -> [PlaylistSource] {
        storedSources
    }

    func saveSources(_ sources: [PlaylistSource]) async {
        storedSources = sources
        saveSourcesCallCount += 1
    }

    func loadChannels() async -> [Channel] {
        storedChannels
    }

    func saveChannels(_ channels: [Channel]) async {
        storedChannels = channels
        saveChannelsCallCount += 1
    }

    func deleteAll() async {
        storedSources = []
        storedChannels = []
        deleteAllCallCount += 1
    }

    /// Test setup helper — seeds storage as if a previous session had saved this.
    func seedSources(_ sources: [PlaylistSource]) {
        storedSources = sources
    }

    /// Test setup helper — seeds storage as if a previous session had saved this.
    func seedChannels(_ channels: [Channel]) {
        storedChannels = channels
    }
}
