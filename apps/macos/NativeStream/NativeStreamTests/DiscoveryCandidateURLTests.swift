// DiscoveryCandidateURLTests
// Unit tests for DiscoveryCandidateURL.build — the pure host/port → URL
// construction extracted from ServerDiscoveryService.resolveAndVerify.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class DiscoveryCandidateURLTests: XCTestCase {

    func testBuildsURLForPlainIPv4Host() {
        let url = DiscoveryCandidateURL.build(hostString: "192.168.1.50", port: 8888)

        XCTAssertEqual(url?.absoluteString, "http://192.168.1.50:8888")
    }

    func testBuildsURLForHostname() {
        let url = DiscoveryCandidateURL.build(hostString: "nativestream.local", port: 8888)

        XCTAssertEqual(url?.absoluteString, "http://nativestream.local:8888")
    }

    func testStripsEn0ZoneSuffix() {
        let url = DiscoveryCandidateURL.build(hostString: "fe80::1%en0", port: 8888)

        // IPv6 literals are bracketed per RFC 3986 §3.2.2 so host/port is unambiguous.
        XCTAssertEqual(url?.absoluteString, "http://[fe80::1]:8888")
    }

    func testStripsLo0ZoneSuffix() {
        let url = DiscoveryCandidateURL.build(hostString: "::1%lo0", port: 8888)

        XCTAssertEqual(url?.absoluteString, "http://[::1]:8888")
    }

    func testLeavesHostUnchangedWhenNoZoneSuffixPresent() {
        let url = DiscoveryCandidateURL.build(hostString: "10.0.0.5", port: 443)

        XCTAssertEqual(url?.absoluteString, "http://10.0.0.5:443")
    }

    func testDifferentPortsProduceDifferentURLs() {
        let low = DiscoveryCandidateURL.build(hostString: "10.0.0.5", port: 80)
        let high = DiscoveryCandidateURL.build(hostString: "10.0.0.5", port: 65535)

        XCTAssertEqual(low?.port, 80)
        XCTAssertEqual(high?.port, 65535)
    }

    func testStripsArbitraryZoneSuffixes() {
        // Zone suffixes are stripped regardless of interface name, not just en0/lo0.
        let url = DiscoveryCandidateURL.build(hostString: "fe80::1%en1", port: 8888)

        XCTAssertEqual(url?.absoluteString, "http://[fe80::1]:8888")
    }
}
