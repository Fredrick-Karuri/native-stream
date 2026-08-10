// ControlSessionTests
// Unit tests for the two ControlSession methods reachable without a live
// WebSocket connection: makeWebSocketURL(from:) and handleMessage(_:).
// Everything else in ControlSession (connect/send/receive/reconnect) requires
// a real or injected URLSessionWebSocketTask, which is out of scope here —
// see the conversation for the protocol-seam alternative if that's wanted later.
//
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream
import SdkGenSwift
import SwiftProtobuf

@MainActor
final class ControlSessionTests: XCTestCase {

    private var session: ControlSession!

    override func setUp() async throws {
        try await super.setUp()
        session = ControlSession()
    }

    override func tearDown() async throws {
        session = nil
        try await super.tearDown()
    }

    // MARK: makeWebSocketURL

    func testMakeWebSocketURLConvertsHTTPToWS() {
        let httpURL = URL(string: "http://localhost:8888")!

        let wsURL = session.makeWebSocketURL(from: httpURL)

        XCTAssertEqual(wsURL?.scheme, "ws")
        XCTAssertEqual(wsURL?.host, "localhost")
        XCTAssertEqual(wsURL?.port, 8888)
        XCTAssertEqual(wsURL?.path, "/ws")
    }

    func testMakeWebSocketURLConvertsHTTPSToWS() {
        // The implementation always sets scheme "ws", even for https input.
        let httpsURL = URL(string: "https://stream.example.com")!

        let wsURL = session.makeWebSocketURL(from: httpsURL)

        XCTAssertEqual(wsURL?.scheme, "ws")
        XCTAssertEqual(wsURL?.host, "stream.example.com")
    }

    func testMakeWebSocketURLReplacesExistingPathWithWs() {
        let urlWithPath = URL(string: "http://localhost:8888/some/other/path")!

        let wsURL = session.makeWebSocketURL(from: urlWithPath)

        XCTAssertEqual(wsURL?.path, "/ws")
    }

    func testMakeWebSocketURLPreservesPort() {
        let urlWithPort = URL(string: "http://192.168.1.50:9999")!

        let wsURL = session.makeWebSocketURL(from: urlWithPort)

        XCTAssertEqual(wsURL?.port, 9999)
    }

    // MARK: handleMessage

    func testHandleMessageYieldsDecodedEnvelopeForValidStringMessage() async throws {
        var envelope = Stream_V1_Envelope()
        envelope.type = .stateUpdate
        envelope.from = "target-1"
        envelope.to = "controller-1"

        let text = try envelope.jsonString()

        var iterator = session.incomingMessages.makeAsyncIterator()
        session.handleMessage(.string(text))

        let received = try await withTimeout(seconds: 1) { await iterator.next() }

        XCTAssertEqual(received?.type, .stateUpdate)
        XCTAssertEqual(received?.from, "target-1")
        XCTAssertEqual(received?.to, "controller-1")
    }

    func testHandleMessageIgnoresDataMessageVariant() async throws {
        var iterator = session.incomingMessages.makeAsyncIterator()
        session.handleMessage(.data(Data([0x01, 0x02])))

        let received = try await withTimeout(seconds: 0.3) { await iterator.next() }

        XCTAssertNil(received)
    }

    func testHandleMessageIgnoresMalformedJSONString() async throws {
        var iterator = session.incomingMessages.makeAsyncIterator()
        session.handleMessage(.string("not valid json"))

        let received = try await withTimeout(seconds: 0.3) { await iterator.next() }

        XCTAssertNil(received)
    }

    func testHandleMessageIgnoresValidJSONThatIsNotAnEnvelope() async throws {
        var iterator = session.incomingMessages.makeAsyncIterator()
        session.handleMessage(.string(#"{"unrelated":"shape"}"#))

        let received = try await withTimeout(seconds: 0.3) { await iterator.next() }

        XCTAssertNil(received)
    }
}

// MARK: - Test helper

/// Awaits `operation`, returning nil if it doesn't complete within `seconds`.
/// Needed because AsyncStream.next() suspends forever when nothing is yielded,
/// which is exactly the case we're asserting on for the "ignored" tests.
private func withTimeout<T>(
    seconds: Double,
    operation: @escaping () async -> T?
) async throws -> T? {
    try await withThrowingTaskGroup(of: T?.self) { group in
        group.addTask { await operation() }
        group.addTask {
            try await Task.sleep(for: .seconds(seconds))
            return nil
        }
        let result = try await group.next()
        group.cancelAll()
        return result
    }
}
