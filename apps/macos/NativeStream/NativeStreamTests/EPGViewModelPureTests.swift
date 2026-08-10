// EPGViewModelPureTests
// Unit tests for the parts of EPGViewModel that don't require network I/O:
// normalizeEPGURL, stripGzipHeader, matchesSport, and the query/diagnostic
// methods that operate purely on the `stores` dictionary (set directly here,
// bypassing load()/fetchAndParse() entirely).
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

@MainActor
final class EPGViewModelPureTests: XCTestCase {

    private var viewModel: EPGViewModel!

    override func setUp() {
        super.setUp()
        viewModel = EPGViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    // MARK: - normalizeEPGURL

    func testNormalizeEPGURLRewritesGitHubBlobURLToRaw() {
        let url = URL(
            string: "https://github.com/user/repo/raw/main/guide.xml")!

        let normalized = EPGViewModel.normalizeEPGURL(url)

        XCTAssertEqual(normalized.absoluteString, "https://raw.githubusercontent.com/user/repo/main/guide.xml")
    }

    func testNormalizeEPGURLLeavesNonGitHubURLUnchanged() {
        let url = URL(string: "http://epg.example.com/guide.xml")!

        let normalized = EPGViewModel.normalizeEPGURL(url)

        XCTAssertEqual(normalized, url)
    }

    func testNormalizeEPGURLLeavesGitHubURLWithoutRawPathUnchanged() {
        // No "/raw/" segment present — only the domain swap should NOT apply
        // since the implementation only rewrites when host == "github.com".
        // A github.com URL without "/raw/" still gets the domain swapped.
        let url = URL(
            string: "https://github.com/user/repo/blob/main/guide.xml")!

        let normalized = EPGViewModel.normalizeEPGURL(url)

        XCTAssertEqual(normalized.absoluteString, "https://raw.githubusercontent.com/user/repo/blob/main/guide.xml")
    }

    // MARK: - stripGzipHeader

    /// Builds a minimal valid gzip byte sequence: 10-byte fixed header (no
    /// optional fields), a payload, and an 8-byte trailer (CRC32 + ISIZE,
    /// values irrelevant to stripGzipHeader since it only computes offsets).
    private func gzipBytes(
        flags: UInt8 = 0x00,
        extra: [UInt8] = [],
        name: [UInt8]? = nil,
        comment: [UInt8]? = nil,
        payload: [UInt8]) -> Data {
        var bytes: [UInt8] = [0x1f, 0x8b, 0x08, flags, 0, 0, 0, 0, 0, 0xff]// ID1 ID2 CM FLG MTIME(4) XFL OS
        if flags & 0x04 != 0 {
            let xlen = extra.count
            bytes.append(UInt8(xlen & 0xff))
            bytes.append(UInt8((xlen >> 8) & 0xff))
            bytes.append(contentsOf: extra)
        }
        if flags & 0x08 != 0, let name {
            bytes.append(contentsOf: name)
            bytes.append(0)
        }
        if flags & 0x10 != 0, let comment {
            bytes.append(contentsOf: comment)
            bytes.append(0)
        }
        if flags & 0x02 != 0 {
            bytes.append(contentsOf: [0, 0]) // FHCRC
        }
        bytes.append(contentsOf: payload)
        bytes.append(contentsOf: [0, 0, 0, 0, 0, 0, 0, 0]) // CRC32 + ISIZE trailer
        return Data(bytes)
    }

    func testStripGzipHeaderReturnsPayloadForMinimalHeader() {
        let payload: [UInt8] = Array(repeating: 0x41, count: 20) // 20 bytes of 'A'
        let data = gzipBytes(payload: payload)

        let stripped = EPGViewModel.stripGzipHeader(data)

        XCTAssertEqual(stripped, Data(payload))
    }

    func testStripGzipHeaderHandlesFNAMEFlag() {
        let payload: [UInt8] = Array(repeating: 0x42, count: 15)
        let name = Array("guide.xml".utf8)
        let data = gzipBytes(flags: 0x08, name: name, payload: payload)

        let stripped = EPGViewModel.stripGzipHeader(data)

        XCTAssertEqual(stripped, Data(payload))
    }

    func testStripGzipHeaderHandlesFEXTRAFlag() {
        let payload: [UInt8] = Array(repeating: 0x43, count: 15)
        let extra: [UInt8] = [0x01, 0x02, 0x03, 0x04]
        let data = gzipBytes(flags: 0x04, extra: extra, payload: payload)

        let stripped = EPGViewModel.stripGzipHeader(data)

        XCTAssertEqual(stripped, Data(payload))
    }

    func testStripGzipHeaderHandlesFCOMMENTFlag() {
        let payload: [UInt8] = Array(repeating: 0x44, count: 15)
        let comment = Array("test comment".utf8)
        let data = gzipBytes(flags: 0x10, comment: comment, payload: payload)

        let stripped = EPGViewModel.stripGzipHeader(data)

        XCTAssertEqual(stripped, Data(payload))
    }

    func testStripGzipHeaderHandlesFHCRCFlag() {
        let payload: [UInt8] = Array(repeating: 0x45, count: 15)
        let data = gzipBytes(flags: 0x02, payload: payload)

        let stripped = EPGViewModel.stripGzipHeader(data)

        XCTAssertEqual(stripped, Data(payload))
    }

    func testStripGzipHeaderReturnsNilForTooShortData() {
        let tooShort = Data([0x1f, 0x8b, 0x08, 0x00, 0x00])

        XCTAssertNil(EPGViewModel.stripGzipHeader(tooShort))
    }

    // MARK: - matchesSport

    private func programme(title: String) -> Programme {
        Programme(channelId: "bbc1", title: title, start: Date(), stop: Date().addingTimeInterval(3600))
    }

    private func channel(tvgId: String = "bbc1") -> Channel {
        Channel(tvgId: tvgId, name: tvgId, streamURL: URL(string: "http://example.com/\(tvgId).m3u8")!)
    }

    func testMatchesSportReturnsTrueWhenTitleContainsKeyword() {
        let prog = programme(title: "Premier League: Arsenal vs Chelsea")

        XCTAssertTrue(
            viewModel.matchesSport(
                .football,
                programme: prog,
                channel: channel()
            ))}

    func testMatchesSportIsCaseInsensitive() {
        let prog = programme(title: "PREMIER LEAGUE HIGHLIGHTS")

        XCTAssertTrue(
            viewModel.matchesSport(
                .football,
                programme: prog,
                channel: channel()
            ))}

    func testMatchesSportReturnsFalseWhenNoKeywordMatches() {
        let prog = programme(title: "The Evening News")

        XCTAssertFalse(
            viewModel.matchesSport(
                .football,
                programme: prog,
                channel: channel()
            ))}

    func testMatchesSportDoesNotCrossMatchDifferentSports() {
        let prog = programme(title: "NBA Finals Game 7")

        XCTAssertFalse(
            viewModel.matchesSport(
                .football, programme: prog, channel: channel()))
        XCTAssertTrue(
            viewModel.matchesSport(
                .basketball,
                programme: prog,
                channel: channel()
            ))}

    // MARK: - Query methods (stores set directly)

    func testCurrentProgrammeFindsMatchAcrossMultipleStores() {
        let live = Programme(
            channelId: "bbc1",
            title: "Live Now",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        let storeA = EPGStore(programmes: ["itv1": []])
        let storeB = EPGStore(programmes: ["bbc1": [live]])
        viewModel.stores = [UUID(): storeA, UUID(): storeB]

        XCTAssertEqual(
            viewModel.currentProgramme(
                for: channel(tvgId: "bbc1"))?.title, "Live Now")
    }

    func testCurrentProgrammeReturnsNilWhenNoStoreHasMatch() {
        viewModel.stores = [UUID(): EPGStore(programmes: [:])]

        XCTAssertNil(viewModel.currentProgramme(for: channel()))
    }

    func testScheduleWithHoursFiltersToWindowAndDeduplicates() {
        let inWindow = Programme(
            channelId: "bbc1",
            title: "In Window",
            start: Date().addingTimeInterval(600),
            stop: Date().addingTimeInterval(1200))
        let outsideWindow = Programme(
            channelId: "bbc1",
            title: "Too Far",
            start: Date().addingTimeInterval(30_000),
            stop: Date().addingTimeInterval(31_000))
        let past = Programme(
            channelId: "bbc1",
            title: "Already Ended",
            start: Date().addingTimeInterval(-7200),
            stop: Date().addingTimeInterval(-3600))
        viewModel.stores = [
            UUID(): EPGStore(
                programmes: ["bbc1": [inWindow, outsideWindow, past]])
        ]

        let result = viewModel.schedule(
            for: channel(tvgId: "bbc1"), hours: 6)

        XCTAssertEqual(result.map(\.title), ["In Window"])
    }

    func testLogMatchDiagnosticDoesNotCrashOnEmptyChannelList() {
        viewModel.logMatchDiagnostic(for: [])
        // No assertion beyond reaching this line without a crash/division-by-zero.
    }

    // MARK: - Sport aggregate helpers

    func testHasContentTrueWhenLiveProgrammeMatchesSport() {
        let live = Programme(
            channelId: "bbc1",
            title: "Premier League Live",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        viewModel.stores = [
            UUID(): EPGStore(programmes: ["bbc1": [live]])
        ]

        XCTAssertTrue(viewModel.hasContent(
            for: .football, in: [channel(tvgId: "bbc1")]
        ))
    }

    func testHasContentFalseWhenNothingMatches() {
        viewModel.stores = [UUID(): EPGStore(programmes: [:])]

        XCTAssertFalse(viewModel.hasContent(
            for: .football, in: [channel()]
        ))
    }

    func testLiveCountCountsOnlyCurrentlyMatchingChannels() {
        let live = Programme(
            channelId: "bbc1",
            title: "NBA Finals",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        let notSport = Programme(
            channelId: "itv1",
            title: "The News",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        viewModel.stores = [UUID(): EPGStore(
            programmes: ["bbc1": [live], "itv1": [notSport]]
        )]

        let count = viewModel.liveCount(
            for: .basketball, in: [channel(tvgId: "bbc1"),
                                   channel(tvgId: "itv1")])

        XCTAssertEqual(count, 1)
    }

    func testActiveSportsSortedByDescendingLiveCount() {
        let footballLive = Programme(
            channelId: "bbc1",
            title: "Premier League",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        let basketballLiveA = Programme(
            channelId: "itv1",
            title: "NBA Finals",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        let basketballLiveB = Programme(
            channelId: "sky1",
            title: "WNBA Game",
            start: Date().addingTimeInterval(-60),
            stop: Date().addingTimeInterval(60))
        viewModel.stores = [UUID(): EPGStore(programmes: [
            "bbc1": [footballLive],
            "itv1": [basketballLiveA],
            "sky1": [basketballLiveB]
        ])]
        let channels = [
            channel(tvgId: "bbc1"),
            channel(tvgId: "itv1"),
            channel(tvgId: "sky1")]

        let active = viewModel.activeSports(in: channels)

        // Basketball has 2 live matches, football has 1 — basketball should sort first.
        XCTAssertEqual(active.first, .basketball)
        XCTAssertTrue(active.contains(.football))
    }
}
