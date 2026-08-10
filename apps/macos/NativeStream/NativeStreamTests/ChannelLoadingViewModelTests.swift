// ChannelLoadingViewModelTests
// Unit tests for ChannelLoadingViewModel: cached-channel loading, fetch
// result application (grouping, EPG URL propagation, error surfacing,
// per-source channel counts), insert, and resetAll. Uses FakeChannelRepository
// and FakeSettingsPersisting — no real network or disk I/O.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

@MainActor
final class ChannelLoadingViewModelTests: XCTestCase {

    private var repository: FakeChannelRepository!
    private var dataStore: FakeSettingsPersisting!
    private var sourceDataStore: FakeSettingsPersisting!
    private var sourceViewModel: SourceViewModel!
    private var settings: SettingsStore!
    private var settingsDefaults: UserDefaults!
    private var settingsSuiteName: String!
    private var viewModel: ChannelLoadingViewModel!

    override func setUp() async throws {
        try await super.setUp()
        repository = FakeChannelRepository()
        dataStore = FakeSettingsPersisting()
        sourceDataStore = FakeSettingsPersisting()
        sourceViewModel = SourceViewModel(dataStore: sourceDataStore, scheduler: RefreshScheduler())

        settingsSuiteName = "ChannelLoadingViewModelTests.\(UUID().uuidString)"
        settingsDefaults = UserDefaults(suiteName: settingsSuiteName)
        settings = SettingsStore(defaults: settingsDefaults)

        viewModel = ChannelLoadingViewModel(
            sourceViewModel: sourceViewModel,
            settings: settings,
            repository: repository,
            dataStore: dataStore
        )
    }

    override func tearDown() async throws {
        settingsDefaults.removePersistentDomain(forName: settingsSuiteName)
        viewModel = nil
        settings = nil
        settingsDefaults = nil
        settingsSuiteName = nil
        sourceViewModel = nil
        sourceDataStore = nil
        dataStore = nil
        repository = nil
        try await super.tearDown()
    }

    private func channel(tvgId: String, groupTitle: String = "Uncategorised", sourceId: String = "") -> Channel {
        Channel(
            tvgId: tvgId, 
            name: tvgId, 
            groupTitle: groupTitle, 
            sourceId: sourceId, 
            streamURL: URL(string: "http://example.com/\(tvgId).m3u8")!)
    }

    private func source(id: UUID = UUID(), label: String = "Source") -> PlaylistSource {
        PlaylistSource(id: id, label: label, url: URL(string: "http://example.com/playlist.m3u")!)
    }

    // MARK: loadCachedChannelsFromDisk

    func testLoadCachedChannelsFromDiskAppliesCachedChannels() async {
        await dataStore.seedChannels([channel(tvgId: "bbc1", groupTitle: "Entertainment")])

        await viewModel.loadCachedChannelsFromDisk()

        XCTAssertEqual(viewModel.channels.map(\.tvgId), ["bbc1"])
        XCTAssertEqual(viewModel.sortedGroupNames, ["Entertainment"])
    }

    func testLoadCachedChannelsFromDiskDoesNothingWhenCacheEmpty() async {
        await viewModel.loadCachedChannelsFromDisk()

        XCTAssertTrue(viewModel.channels.isEmpty)
    }

    // MARK: loadAll — basic application

    func testLoadAllAppliesChannelsFromRepositoryResult() async {
        let fetched = [channel(
            tvgId: "bbc1", 
            groupTitle: "Entertainment"), 
            channel(tvgId: "itv1", groupTitle: "News")]
        await repository.setResult(ChannelFetchResult(channels: fetched, perSource: []))

        await viewModel.loadAll()

        XCTAssertEqual(viewModel.channels.map(\.tvgId).sorted(), ["bbc1", "itv1"])
        XCTAssertEqual(viewModel.sortedGroupNames, ["Entertainment", "News"])
    }

    func testLoadAllSetsIsLoadingFalseAfterCompletionWhenStartingWithNoCache() async {
        await repository.setResult(ChannelFetchResult(channels: [], perSource: []))

        await viewModel.loadAll()

        XCTAssertFalse(viewModel.isLoading)
    }

    func testLoadAllIsNoOpWhenAlreadyLoading() async {
        viewModel.isLoading = true

        await viewModel.loadAll()

        let callCount = await repository.fetchChannelsCallCount
        XCTAssertEqual(callCount, 0)
    }

    func testLoadAllPassesSourceViewModelSourcesToRepository() async {
        let configuredSource = source(label: "Configured")
        sourceViewModel.sources = [configuredSource]
        await repository.setResult(ChannelFetchResult(channels: [], perSource: []))

        await viewModel.loadAll()

        let requested = await repository.lastRequestedSources
        XCTAssertEqual(requested.map(\.label), ["Configured"])
    }

    func testLoadAllSavesChannelsToDataStore() async {
        let fetched = [channel(tvgId: "bbc1")]
        await repository.setResult(ChannelFetchResult(channels: fetched, perSource: []))

        await viewModel.loadAll()

        let saved = await dataStore.storedChannels
        XCTAssertEqual(saved.map(\.tvgId), ["bbc1"])
    }

    func testLoadAllMarksAllSourcesFetchedOnSourceViewModel() async {
        sourceViewModel.sources = [source()]
        await repository.setResult(ChannelFetchResult(channels: [], perSource: []))

        await viewModel.loadAll()

        XCTAssertNotNil(sourceViewModel.sources.first?.lastFetched)
    }

    // MARK: loadAll — per-source result application

    func testLoadAllUpdatesChannelCountOnSourceViewModelPerSource() async {
        let src = source(label: "Source A")
        sourceViewModel.sources = [src]
        let perSource = SourceFetchResult(
            sourceID: src.id, 
            channels: [
                channel(tvgId: "bbc1"), 
                channel(tvgId: "itv1")], 
                discoveredEPGURL: nil, 
                error: nil)
        await repository.setResult(
            ChannelFetchResult(channels: perSource.channels, perSource: [perSource]))

        await viewModel.loadAll()

        XCTAssertEqual(sourceViewModel.sources.first?.channelCount, 2)
    }

    func testLoadAllRecordsDiscoveredEPGURLOnSourceViewModel() async {
        let src = source(label: "Source A")
        sourceViewModel.sources = [src]
        let epgURL = URL(string: "http://example.com/guide.xml")!
        let perSource = SourceFetchResult(
            sourceID: src.id, 
            channels: [], 
            discoveredEPGURL: epgURL, 
            error: nil)
        await repository.setResult(ChannelFetchResult(channels: [], perSource: [perSource]))

        await viewModel.loadAll()

        XCTAssertEqual(sourceViewModel.sources.first?.epgURLString, epgURL.absoluteString)
    }

    func testLoadAllSetsSettingsEpgURLStringWhenCurrentlyEmpty() async {
        XCTAssertEqual(settings.epgURLString, "")
        let src = source(label: "Source A")
        sourceViewModel.sources = [src]
        let epgURL = URL(string: "http://example.com/guide.xml")!
        let perSource = SourceFetchResult(sourceID: src.id, channels: [], discoveredEPGURL: epgURL, error: nil)
        await repository.setResult(ChannelFetchResult(channels: [], perSource: [perSource]))

        await viewModel.loadAll()

        XCTAssertEqual(settings.epgURLString, epgURL.absoluteString)
    }

    func testLoadAllDoesNotOverwriteExistingSettingsEpgURLString() async {
        settings.epgURLString = "http://example.com/existing.xml"
        let src = source(label: "Source A")
        sourceViewModel.sources = [src]
        let perSource = SourceFetchResult(
            sourceID: src.id, 
            channels: [], 
            discoveredEPGURL: URL(string: "http://example.com/new.xml")!, 
            error: nil)
        await repository.setResult(ChannelFetchResult(channels: [], perSource: [perSource]))

        await viewModel.loadAll()

        XCTAssertEqual(settings.epgURLString, "http://example.com/existing.xml")
    }

    func testLoadAllSurfacesErrorFromPerSourceResult() async {
        let src = source(label: "Source A")
        sourceViewModel.sources = [src]
        let fetchError = AppError.playlistFetchFailed(url: src.url, underlying: URLError(.notConnectedToInternet))
        let perSource = SourceFetchResult(
            sourceID: src.id, 
            channels: [], 
            discoveredEPGURL: nil, 
            error: fetchError)
        await repository.setResult(ChannelFetchResult(channels: [], perSource: [perSource]))

        await viewModel.loadAll()

        XCTAssertNotNil(viewModel.error)
    }

    func testLoadAllClearsPreviousErrorAtStartOfNewLoad() async {
        viewModel.error = .networkUnavailable
        await repository.setResult(ChannelFetchResult(channels: [channel(tvgId: "bbc1")], perSource: []))

        await viewModel.loadAll()

        XCTAssertNil(viewModel.error)
    }

    // MARK: refresh(source:)

    func testRefreshDelegatesToLoadAll() async {
        await repository.setResult(ChannelFetchResult(channels: [channel(tvgId: "bbc1")], perSource: []))

        await viewModel.refresh(source: source())

        XCTAssertEqual(viewModel.channels.map(\.tvgId), ["bbc1"])
        let callCount = await repository.fetchChannelsCallCount
        XCTAssertEqual(callCount, 1)
    }

    // MARK: insert

    func testInsertAppendsChannelAndRegroups() {
        viewModel.insert(channel(tvgId: "bbc1", groupTitle: "Entertainment"))

        XCTAssertEqual(viewModel.channels.map(\.tvgId), ["bbc1"])
        XCTAssertEqual(viewModel.sortedGroupNames, ["Entertainment"])
    }

    func testInsertPreservesExistingChannels() async {
        await repository.setResult(ChannelFetchResult(channels: [channel(tvgId: "bbc1")], perSource: []))
        await viewModel.loadAll()

        viewModel.insert(channel(tvgId: "itv1"))

        XCTAssertEqual(viewModel.channels.map(\.tvgId).sorted(), ["bbc1", "itv1"])
    }

    // MARK: resetAll

    func testResetAllClearsChannelsGroupsAndError() async {
        await repository.setResult(ChannelFetchResult(channels: [channel(tvgId: "bbc1")], perSource: []))
        await viewModel.loadAll()
        viewModel.error = .networkUnavailable

        viewModel.resetAll()

        XCTAssertTrue(viewModel.channels.isEmpty)
        XCTAssertTrue(viewModel.groups.isEmpty)
        XCTAssertTrue(viewModel.sortedGroupNames.isEmpty)
        XCTAssertNil(viewModel.error)
    }
}
