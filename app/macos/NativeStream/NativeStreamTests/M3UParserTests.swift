// NS-022: M3UParserTests
// Unit tests for the M3U playlist parser.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class M3UParserTests: XCTestCase {

    var parser: M3UParser!

    override func setUp() {
        super.setUp()
        parser = M3UParser()
    }

    // MARK: - Valid playlists

    func testSingleChannelParsesCorrectly() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="Sky1" tvg-logo="https://logos.example.com/sky1.png" group-title="Football",Sky Sports 1
        https://stream.example.com/sky1/index.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels.count, 1)
        let ch = result.channels[0]
        XCTAssertEqual(ch.tvgId, "Sky1")
        XCTAssertEqual(ch.name, "Sky Sports 1")
        XCTAssertEqual(ch.groupTitle, "Football")
        XCTAssertEqual(ch.logoURL?.absoluteString, "https://logos.example.com/sky1.png")
        XCTAssertEqual(ch.streamURL.absoluteString, "https://stream.example.com/sky1/index.m3u8")
        XCTAssertTrue(result.warnings.isEmpty)
    }

    func testThreeChannelPlaylist() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="Ch1" group-title="Cricket",Channel One
        https://stream.example.com/ch1.m3u8
        #EXTINF:-1 tvg-id="Ch2" group-title="Football",Channel Two
        https://stream.example.com/ch2.m3u8
        #EXTINF:-1 tvg-id="Ch3" group-title="Cricket",Channel Three
        https://stream.example.com/ch3.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels.count, 3)
        XCTAssertEqual(result.channels[1].name, "Channel Two")
        XCTAssertEqual(result.channels[2].groupTitle, "Cricket")
    }

    func testGroupTitleWithSpaces() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 group-title="Premier League HD",EPL Channel
        https://stream.example.com/epl.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels[0].groupTitle, "Premier League HD")
    }

    func testStreamURLWithQueryString() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="SS1",SuperSport 1
        https://stream.example.com/ss1/index.m3u8?token=abc123&expires=9999999999
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels.count, 1)
        XCTAssertTrue(result.channels[0].streamURL.absoluteString.contains("token=abc123"))
    }

    // MARK: - Missing optional attributes

    func testMissingTvgId() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 group-title="Football",BBC One
        https://stream.example.com/bbc1.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels.count, 1)
        XCTAssertEqual(result.channels[0].tvgId, "")
    }

    func testMissingTvgLogo() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="BBC1",BBC One
        https://stream.example.com/bbc1.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertNil(result.channels[0].logoURL)
    }

    func testMissingGroupTitle() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="BBC1",BBC One
        https://stream.example.com/bbc1.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels[0].groupTitle, "Uncategorised")
    }

    // MARK: - Malformed entries — never crash, always warn

    func testMalformedExtInfSkippedWithWarning() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="Good",Good Channel
        https://stream.example.com/good.m3u8
        #EXTINF:THIS IS BROKEN NO COMMA
        https://stream.example.com/bad.m3u8
        #EXTINF:-1 tvg-id="Also Good",Also Good
        https://stream.example.com/alsogood.m3u8
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        // "Good" and "Also Good" should parse; broken EXTINF should warn and use fallback name
        XCTAssertEqual(result.channels.count, 3)
        XCTAssertFalse(result.warnings.isEmpty)
    }

    func testInvalidStreamURLSkipped() async throws {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="Good",Good Channel
        https://stream.example.com/good.m3u8
        #EXTINF:-1 tvg-id="Bad",Bad Channel
        not a valid url !!@@##
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels.count, 1)
        XCTAssertEqual(result.warnings.count, 1)
    }

    func testEmptyFileReturnsEmpty() async throws {
        let result = try await parser.parse(data: Data())
        XCTAssertEqual(result.channels.count, 0)
        XCTAssertTrue(result.warnings.isEmpty)
    }

    func testFileWithOnlyComments() async throws {
        let m3u = """
        #EXTM3U
        # This is a comment
        # Another comment
        """
        let result = try await parser.parse(data: Data(m3u.utf8))
        XCTAssertEqual(result.channels.count, 0)
    }

    func testLatin1EncodedFile() async throws {
        // Some M3U files use Latin-1 encoding
        let m3u = "#EXTM3U\n#EXTINF:-1,Caf\u{E9} Sport\nhttps://stream.example.com/cafe.m3u8"
        let data = m3u.data(using: .isoLatin1)!
        let result = try await parser.parse(data: data)
        XCTAssertEqual(result.channels.count, 1)
    }

    // MARK: - Performance

    func testParses500ChannelsUnderOneSecond() async throws {
        var lines = ["#EXTM3U"]
        for i in 1...500 {
            lines.append("#EXTINF:-1 tvg-id=\"Ch\(i)\" group-title=\"Group\(i % 10)\",Channel \(i)")
            lines.append("https://stream.example.com/ch\(i)/index.m3u8")
        }
        let data = Data(lines.joined(separator: "\n").utf8)

        let start = Date()
        let result = try await parser.parse(data: data)
        let elapsed = Date().timeIntervalSince(start)

        XCTAssertEqual(result.channels.count, 500)
        XCTAssertLessThan(elapsed, 1.0, "Parser took \(elapsed)s — should be under 1s")
    }
}