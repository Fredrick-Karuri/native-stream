//
//  ServerURLResolverTests.swift

import XCTest
@testable import NativeStream

final class ServerURLResolverTests: XCTestCase {

    func testReturnsHostedDefaultWhenNothingSet() {
        let result = ServerURLResolver.resolve(manualOverride: nil, discoveredLANURL: nil)

        XCTAssertEqual(result.url, ServerURLResolver.hostedDefaultURL)
        XCTAssertEqual(result.source, .hostedDefault)
    }

    func testReturnsLANURLWhenDiscoveredAndNoOverride() {
        let result = ServerURLResolver.resolve(
            manualOverride: nil,
            discoveredLANURL: "http://192.168.1.50:8888"
        )

        XCTAssertEqual(result.url, "http://192.168.1.50:8888")
        XCTAssertEqual(result.source, .lanDiscovered)
    }

    func testManualOverrideTakesPrecedenceOverLAN() {
        let result = ServerURLResolver.resolve(
            manualOverride: "http://manual.example.com",
            discoveredLANURL: "http://192.168.1.50:8888"
        )

        XCTAssertEqual(result.url, "http://manual.example.com")
        XCTAssertEqual(result.source, .manualOverride)
    }

    func testEmptyManualOverrideIsTreatedAsUnset() {
        let result = ServerURLResolver.resolve(
            manualOverride: "",
            discoveredLANURL: "http://192.168.1.50:8888"
        )

        XCTAssertEqual(result.source, .lanDiscovered)
    }

    func testEmptyDiscoveredLANURLFallsBackToHostedDefault() {
        let result = ServerURLResolver.resolve(manualOverride: nil, discoveredLANURL: "")

        XCTAssertEqual(result.source, .hostedDefault)
    }
}
