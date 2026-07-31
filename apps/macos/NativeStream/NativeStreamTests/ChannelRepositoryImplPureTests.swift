// ChannelRepositoryImplPureTests
// Unit tests for the pure, nonisolated helper methods on ChannelRepositoryImpl:
// isLocalSource, taggedChannel, and deduplicated. No network or parser
// involved — fetchChannels/fetchSingleSource (which need M3UParser +
// network mocking) are out of scope here.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class ChannelRepositoryImplPureTests: XCTestCase {

    private var repository: ChannelRepositoryImpl!

    override func setUp() {
        super.setUp()
        repository = ChannelRepositoryImpl()
    }

    override func tearDown() {
        repository = nil
        super.tearDown()
    }

    private func source(urlString: String) -> PlaylistSource {
        PlaylistSource(label: "Source", url: URL(string: urlString)!)
    }

    private func channel(tvgId: String, sourceId: String = "") -> Channel {
        Channel(tvgId: tvgId, name: tvgId, sourceId: sourceId, streamURL: URL(string: "http://example.com/\(tvgId).m3u8")!)
    }

    // MARK: isLocalSource

    func testIsLocalSourceTrueForLocalhostHost() {
        XCTAssertTrue(repository.isLocalSource(source(urlString: "http://localhost:8888/playlist.m3u")))
    }

    func testIsLocalSourceTrueForLoopbackIPHost() {
        XCTAssertTrue(repository.isLocalSource(source(urlString: "http://127.0.0.1:8888/playlist.m3u")))
    }

    func testIsLocalSourceFalseForRemoteHost() {
        XCTAssertFalse(repository.isLocalSource(source(urlString: "http://iptv.example.com/playlist.m3u")))
    }

    func testIsLocalSourceFalseForLANHostThatIsNotLoopback() {
        // A LAN IP is still a "local network" address in casual terms, but not
        // one of the two exact hostnames this implementation checks for.
        XCTAssertFalse(repository.isLocalSource(source(urlString: "http://192.168.1.50:8888/playlist.m3u")))
    }

    // MARK: taggedChannel

    func testTaggedChannelSetsSourceIdToGivenUUID() {
        let original = channel(tvgId: "bbc1", sourceId: "")
        let sourceID = UUID()

        let tagged = repository.taggedChannel(original, sourceID: sourceID)

        XCTAssertEqual(tagged.sourceId, sourceID.uuidString)
    }

    func testTaggedChannelOverwritesExistingSourceId() {
        let original = channel(tvgId: "bbc1", sourceId: "old-source-id")
        let sourceID = UUID()

        let tagged = repository.taggedChannel(original, sourceID: sourceID)

        XCTAssertEqual(tagged.sourceId, sourceID.uuidString)
    }

    func testTaggedChannelPreservesOtherFields() {
        let original = Channel(
            tvgId: "bbc1", name: "BBC One", groupTitle: "Entertainment",
            subGroupTitle: "Drama", sourceId: "old",
            logoURL: URL(string: "http://example.com/logo.png"),
            streamURL: URL(string: "http://example.com/bbc1.m3u8")!,
            streamHeaders: ["User-Agent": "NativeStream"]
        )

        let tagged = repository.taggedChannel(original, sourceID: UUID())

        XCTAssertEqual(tagged.tvgId, "bbc1")
        XCTAssertEqual(tagged.name, "BBC One")
        XCTAssertEqual(tagged.groupTitle, "Entertainment")
        XCTAssertEqual(tagged.subGroupTitle, "Drama")
        XCTAssertEqual(tagged.logoURL, URL(string: "http://example.com/logo.png"))
        XCTAssertEqual(tagged.streamURL, URL(string: "http://example.com/bbc1.m3u8"))
        XCTAssertEqual(tagged.streamHeaders, ["User-Agent": "NativeStream"])
    }

    // MARK: deduplicated

    func testDeduplicatedRemovesChannelsWithSameID() {
        let channels = [
            channel(tvgId: "bbc1"),
            channel(tvgId: "bbc1"), // duplicate id
            channel(tvgId: "itv1")
        ]

        let result = repository.deduplicated(channels)

        XCTAssertEqual(result.map(\.tvgId), ["bbc1", "itv1"])
    }

    func testDeduplicatedPreservesFirstOccurrenceOrder() {
        let first = channel(tvgId: "bbc1")
        var second = channel(tvgId: "bbc1")
        // Same id (tvgId drives Channel.id), different name — first occurrence should win.
        second = Channel(tvgId: "bbc1", name: "Different Name", streamURL: second.streamURL)
        let channels = [first, second]

        let result = repository.deduplicated(channels)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.name, first.name)
    }

    func testDeduplicatedReturnsEmptyForEmptyInput() {
        XCTAssertTrue(repository.deduplicated([]).isEmpty)
    }

    func testDeduplicatedKeepsAllChannelsWhenNoDuplicates() {
        let channels = [channel(tvgId: "bbc1"), channel(tvgId: "itv1"), channel(tvgId: "channel4")]

        let result = repository.deduplicated(channels)

        XCTAssertEqual(result.count, 3)
    }
}
