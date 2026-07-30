// EPGParserTests
// Unit tests for EPGParser's XMLTV parsing: date/timezone parsing, programme
// extraction, and malformed-input handling.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class EPGParserTests: XCTestCase {

    private var parser: EPGParser!

    override func setUp() {
        super.setUp()
        parser = EPGParser()
    }

    override func tearDown() {
        parser = nil
        super.tearDown()
    }

    // MARK: Helpers

    private func utcDate(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int, _ second: Int = 0) -> Date {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = day
        comps.hour = hour; comps.minute = minute; comps.second = second
        comps.timeZone = TimeZone(identifier: "UTC")
        return Calendar(identifier: .gregorian).date(from: comps)!
    }

    private func xmltv(_ programmesXML: String) -> Data {
        Data("""
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>\(programmesXML)</tv>
        """.utf8)
    }

    // MARK: Basic parsing

    func testParsesSingleProgrammeWithUTCTimezone() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>News at Ten</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)
        let schedule = store.schedule(for: "bbc1")

        XCTAssertEqual(schedule.count, 1)
        XCTAssertEqual(schedule[0].title, "News at Ten")
        XCTAssertEqual(schedule[0].channelId, "bbc1")
        XCTAssertEqual(schedule[0].start, utcDate(2024, 6, 7, 14, 30, 0))
        XCTAssertEqual(schedule[0].stop, utcDate(2024, 6, 7, 15, 30, 0))
    }

    func testParsesMultipleProgrammesForSameChannelSortedByStart() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607163000 +0000" stop="20240607173000 +0000">
            <title>Second Show</title>
        </programme>
        <programme channel="bbc1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>First Show</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)
        let schedule = store.schedule(for: "bbc1")

        XCTAssertEqual(schedule.map(\.title), ["First Show", "Second Show"])
    }

    func testParsesProgrammesAcrossMultipleChannels() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>BBC Show</title>
        </programme>
        <programme channel="itv1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>ITV Show</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)

        XCTAssertEqual(store.channelCount, 2)
        XCTAssertEqual(store.knownChannelIds, ["bbc1", "itv1"])
    }

    func testTitleWithSurroundingWhitespaceIsTrimmed() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>
                Padded Title
            </title>
        </programme>
        """)

        let store = try parser.parse(data: xml)

        XCTAssertEqual(store.schedule(for: "bbc1").first?.title, "Padded Title")
    }

    // MARK: Timezone offsets

    func testParsesPositiveTimezoneOffset() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 +0200" stop="20240607153000 +0200">
            <title>CET Show</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)
        let programme = try XCTUnwrap(store.schedule(for: "bbc1").first)

        // 14:30 +0200 == 12:30 UTC
        XCTAssertEqual(programme.start, utcDate(2024, 6, 7, 12, 30, 0))
    }

    func testParsesNegativeTimezoneOffset() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 -0500" stop="20240607153000 -0500">
            <title>EST Show</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)
        let programme = try XCTUnwrap(store.schedule(for: "bbc1").first)

        // 14:30 -0500 == 19:30 UTC
        XCTAssertEqual(programme.start, utcDate(2024, 6, 7, 19, 30, 0))
    }

    func testMalformedTimezoneOffsetFallsBackToUTC() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 garbage" stop="20240607153000 garbage">
            <title>Fallback Show</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)
        let programme = try XCTUnwrap(store.schedule(for: "bbc1").first)

        XCTAssertEqual(programme.start, utcDate(2024, 6, 7, 14, 30, 0))
    }

    // MARK: Malformed / incomplete entries are skipped, not thrown

    func testProgrammeMissingChannelAttributeIsSkipped() throws {
        let xml = xmltv("""
        <programme start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>No Channel</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)

        XCTAssertEqual(store.programmeCount, 0)
    }

    func testProgrammeMissingTitleIsSkipped() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="20240607143000 +0000" stop="20240607153000 +0000">
        </programme>
        """)

        let store = try parser.parse(data: xml)

        XCTAssertEqual(store.programmeCount, 0)
    }

    func testProgrammeWithUnparsableStartDateIsSkipped() throws {
        let xml = xmltv("""
        <programme channel="bbc1" start="not-a-date" stop="20240607153000 +0000">
            <title>Bad Start</title>
        </programme>
        """)

        let store = try parser.parse(data: xml)

        XCTAssertEqual(store.programmeCount, 0)
    }

    func testEmptyTvElementProducesEmptyStore() throws {
        let xml = xmltv("")

        let store = try parser.parse(data: xml)

        XCTAssertEqual(store.programmeCount, 0)
        XCTAssertEqual(store.channelCount, 0)
    }

    // MARK: Invalid XML throws

    func testInvalidXMLThrowsError() {
        let malformed = Data("<tv><programme>not closed".utf8)

        XCTAssertThrowsError(try parser.parse(data: malformed))
    }

    // MARK: Reset between parses

    func testParserCanBeReusedForSuccessiveParses() throws {
        let firstXML = xmltv("""
        <programme channel="bbc1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>First Parse</title>
        </programme>
        """)
        let secondXML = xmltv("""
        <programme channel="itv1" start="20240607143000 +0000" stop="20240607153000 +0000">
            <title>Second Parse</title>
        </programme>
        """)

        _ = try parser.parse(data: firstXML)
        let secondStore = try parser.parse(data: secondXML)

        // Second parse should not carry over state from the first.
        XCTAssertEqual(secondStore.knownChannelIds, ["itv1"])
        XCTAssertNil(secondStore.schedule(for: "bbc1").first)
    }
}
