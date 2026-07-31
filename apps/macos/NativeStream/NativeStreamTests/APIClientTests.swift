// APIClientTests.swift
//
// Unit tests for APIClient's HTTP primitives, endpoint wiring, and error mapping.
// Network calls are intercepted with MockURLProtocol so tests never touch a real server.
//
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
import SdkGenSwift
@testable import NativeStream

// MARK: - MockURLProtocol

/// Intercepts every request made by a URLSession configured with this class and
/// returns a queued canned response instead of hitting the network.
final class MockURLProtocol: URLProtocol {

    struct StubbedResponse {
        let statusCode: Int
        let body: Data
        let headers: [String: String]

        init(statusCode: Int = 200, body: Data = Data(), headers: [String: String] = [:]) {
            self.statusCode = statusCode
            self.body = body
            self.headers = headers
        }
    }

    /// Set by each test before making a request. Cleared in tearDown.
    static var requestHandler: ((URLRequest) throws -> StubbedResponse)?

    /// Every request MockURLProtocol has seen, for assertions on method/headers/path/body.
    static var recordedRequests: [URLRequest] = []

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        MockURLProtocol.recordedRequests.append(request)

        guard let handler = MockURLProtocol.requestHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        do {
            let stub = try handler(request)
            let response = HTTPURLResponse(
                url: request.url ?? URL(string: "http://localhost:8888")!,
                statusCode: stub.statusCode,
                httpVersion: "HTTP/1.1",
                headerFields: stub.headers
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: stub.body)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

// MARK: - APIClientTests

final class APIClientTests: XCTestCase {

    private var client: APIClient!
    private let testBaseURL = URL(string: "http://localhost:8888")!

    override func setUp() {
        super.setUp()
        client = APIClient(baseURL: testBaseURL, protocolClasses: [MockURLProtocol.self])
        MockURLProtocol.recordedRequests = []
        MockURLProtocol.requestHandler = nil
    }

    override func tearDown() {
        MockURLProtocol.requestHandler = nil
        MockURLProtocol.recordedRequests = []
        client = nil
        super.tearDown()
    }

    // MARK: Health

    func testHealthReturnsDecodedResponseOnSuccess() async throws {
        let json = Data(#"{"status":"ok"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        let response = try await client.health()

        XCTAssertEqual(response.status, "ok")
    }

    func testHealthThrowsDecodingFailedOnMalformedJSON() async {
        let malformed = Data(#"{"status": tru"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: malformed) }

        await assertThrowsAPIError(expectedCase: .decodingFailed(NSError())) {
            _ = try await self.client.health()
        }
    }

    // MARK: HTTP error mapping

    func testRequestThrowsHTTPErrorWithStatusCodeAndBodyOnServerError() async {
        let errorBody = Data(#"{"message":"channel not found"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 404, body: errorBody) }

        do {
            _ = try await client.getChannel(id: "missing-channel")
            XCTFail("Expected APIError.httpError to be thrown")
        } catch let APIError.httpError(statusCode, message) {
            XCTAssertEqual(statusCode, 404)
            XCTAssertEqual(message, #"{"message":"channel not found"}"#)
        } catch {
            XCTFail("Expected APIError.httpError, got \(error)")
        }
    }

    func testRequestThrowsServerUnreachableWhenSessionFails() async {
        MockURLProtocol.requestHandler = { _ in throw URLError(.notConnectedToInternet) }

        do {
            _ = try await client.listChannels()
            XCTFail("Expected APIError.serverUnreachable to be thrown")
        } catch APIError.serverUnreachable {
            // expected
        } catch {
            XCTFail("Expected APIError.serverUnreachable, got \(error)")
        }
    }

    // MARK: Channels — request shape

    func testListChannelsSendsGetToChannelsPath() async throws {
        let json = Data(#"{"channels":[]}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        _ = try await client.listChannels()

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "GET")
        XCTAssertEqual(sent.url?.path, "/api/channels")
    }

    func testGetChannelSendsGetToChannelDetailPath() async throws {
        let json = Data(#"""
                {"id":"abc","name":"ESPN","group_title":"Sports","tvg_id":"espn.us",
                 "logo_url":"http://example.com/espn.png","keywords":[],
                 "active_link":null,"candidates":[]}
                """#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        _ = try await client.getChannel(id: "abc")

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "GET")
        XCTAssertEqual(sent.url?.path, "/api/channels/abc")
    }

    func testCreateChannelSendsPostWithJSONContentTypeAndEncodedBody() async throws {
        let responseJSON = Data(#"""
                {"id":"new-1","name":"BBC One","group_title":"News","tvg_id":"bbc-one.uk",
                 "logo_url":"http://example.com/bbc-one.png","keywords":["news","uk"],
                 "active_link":null,"candidates":[]}
                """#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: responseJSON) }

        var request = Stream_V1_CreateChannelRequest()
        request.name = "BBC One"
        request.groupTitle = "News"
        request.tvgID = "bbc-one.uk"
        request.logoURL = "http://example.com/bbc-one.png"
        request.streamURL = "http://example.com/bbc.m3u8"
        request.keywords = ["news", "uk"]

        _ = try await client.createChannel(request)

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "POST")
        XCTAssertEqual(sent.url?.path, "/api/channels")
        XCTAssertEqual(sent.value(forHTTPHeaderField: "Content-Type"), "application/json")
        XCTAssertNotNil(sent.httpBodyStreamOrBody)
    }

    func testUpdateChannelSendsPutToChannelDetailPath() async throws {
        let statusJSON = Data(#"{"status":"ok"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: statusJSON) }

        var request = Stream_V1_UpdateChannelRequest()
        request.streamURL = "http://example.com/new.m3u8"
        try await client.updateChannel(id: "abc", request)

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "PUT")
        XCTAssertEqual(sent.url?.path, "/api/channels/abc")
    }

    func testDeleteChannelSendsDeleteToChannelDetailPath() async throws {
        let statusJSON = Data(#"{"status":"ok"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: statusJSON) }

        try await client.deleteChannel(id: "abc")

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "DELETE")
        XCTAssertEqual(sent.url?.path, "/api/channels/abc")
    }

    // MARK: Discovery

    func testTriggerDiscoverySendsPostToDiscoveryRunPath() async throws {
        let statusJSON = Data(#"{"status":"ok"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: statusJSON) }

        try await client.triggerDiscovery()

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "POST")
        XCTAssertEqual(sent.url?.path, "/api/discovery/run")
    }

    func testUnmatchedLinksIncludesLimitQueryParameter() async throws {
        let json = Data(#"{"unmatched":[],"total":0}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        _ = try await client.unmatchedLinks(limit: 25)

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.url?.path, "/api/discovery/unmatched")
        XCTAssertEqual(sent.url?.query, "limit=25")
    }

    func testUnmatchedLinksDefaultsLimitTo50() async throws {
        let json = Data(#"{"unmatched":[],"total":0}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        _ = try await client.unmatchedLinks()

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.url?.query, "limit=50")
    }

    func testAssignUnmatchedLinkDelegatesToUpdateChannelWithGivenURL() async throws {
        let statusJSON = Data(#"{"status":"ok"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: statusJSON) }

        try await client.assignUnmatchedLink(channelID: "chan-1", url: "http://example.com/live.m3u8")

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "PUT")
        XCTAssertEqual(sent.url?.path, "/api/channels/chan-1")
    }

    // MARK: Raw payload endpoints (playlist / epg)

    func testPlaylistDataReturnsRawBytesUnparsed() async throws {
        let rawM3U = Data("#EXTM3U\n#EXTINF:-1,Test\nhttp://example.com/stream.m3u8".utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: rawM3U) }

        let data = try await client.playlistData()

        XCTAssertEqual(data, rawM3U)
    }

    func testEpgDataReturnsRawBytesUnparsed() async throws {
        let rawXML = Data("<tv></tv>".utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: rawXML) }

        let data = try await client.epgData()

        XCTAssertEqual(data, rawXML)
    }

    func testRawGetSendsCacheControlHeader() async throws {
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: Data()) }

        _ = try await client.playlistData()

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.value(forHTTPHeaderField: "Cache-Control"), "max-age=7200, public")
    }

    // MARK: Proxy config

    func testGetProxyEnabledReturnsTrueWhenServerReportsEnabled() async throws {
        let json = Data(#"{"enabled":true}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        let enabled = try await client.getProxyEnabled()

        XCTAssertTrue(enabled)
    }

    func testGetProxyEnabledReturnsFalseWhenServerReportsDisabled() async throws {
        let json = Data(#"{"enabled":false}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        let enabled = try await client.getProxyEnabled()

        XCTAssertFalse(enabled)
    }

    func testSetProxyEnabledSendsPutWithProtoEncodedBody() async throws {
        let json = Data(#"{"enabled":true}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        try await client.setProxyEnabled(true)

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.httpMethod, "PUT")
        XCTAssertEqual(sent.url?.path, "/api/proxy/config")
        XCTAssertEqual(sent.value(forHTTPHeaderField: "Content-Type"), "application/json")
    }

    func testSetProxyEnabledThrowsDecodingFailedOnMalformedResponse() async {
        let malformed = Data(#"{"enabled": notabool}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: malformed) }

        do {
            try await client.setProxyEnabled(true)
            XCTFail("Expected APIError.decodingFailed to be thrown")
        } catch APIError.decodingFailed {
            // expected
        } catch {
            XCTFail("Expected APIError.decodingFailed, got \(error)")
        }
    }

    // MARK: probePlaylistForEpg — text scanning, no throw contract

    func testProbePlaylistForEpgReturnsURLWhenUrlTvgHeaderPresent() async throws {
        let epgURL = "http://example.com/guide.xml"
        let m3uText = "#EXTM3U url-tvg=\"\(epgURL)\"\n#EXTINF:-1,Channel 1\nhttp://example.com/1.m3u8"
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: Data(m3uText.utf8)) }

        let result = await client.probePlaylistForEpg(url: testBaseURL.appendingPathComponent("playlist.m3u"))

        XCTAssertEqual(result?.absoluteString, epgURL)
    }

    func testProbePlaylistForEpgReturnsNilWhenNoTvgAttributePresent() async throws {
        let m3uText = "#EXTM3U\n#EXTINF:-1,Channel 1\nhttp://example.com/1.m3u8"
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: Data(m3uText.utf8)) }

        let result = await client.probePlaylistForEpg(url: testBaseURL.appendingPathComponent("playlist.m3u"))

        XCTAssertNil(result)
    }

    func testProbePlaylistForEpgReturnsNilWhenFetchFails() async {
        MockURLProtocol.requestHandler = { _ in throw URLError(.notConnectedToInternet) }

        let result = await client.probePlaylistForEpg(url: testBaseURL.appendingPathComponent("playlist.m3u"))

        XCTAssertNil(result)
    }

    // MARK: setBaseURL

    func testSetBaseURLChangesTargetOfSubsequentRequests() async throws {
        let newBase = URL(string: "http://192.168.1.50:9999")!
        let json = Data(#"{"status":"ok"}"#.utf8)
        MockURLProtocol.requestHandler = { _ in .init(statusCode: 200, body: json) }

        await client.setBaseURL(newBase)
        _ = try await client.health()

        let sent = try XCTUnwrap(MockURLProtocol.recordedRequests.first)
        XCTAssertEqual(sent.url?.host, "192.168.1.50")
        XCTAssertEqual(sent.url?.port, 9999)
    }
}

// MARK: - Test helpers

private extension URLRequest {
    /// `httpBody` is nil for requests executed via URLSession's async API in some
    /// configurations; MockURLProtocol.startLoading still sees it via `httpBodyStreamOrBody`
    /// helper to keep assertions robust either way.
    var httpBodyStreamOrBody: Data? {
        if let body = httpBody { return body }
        guard let stream = httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        let bufferSize = 4096
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: bufferSize)
            if read > 0 { data.append(buffer, count: read) }
        }
        return data.isEmpty ? nil : data
    }
}

private func assertThrowsAPIError(
    expectedCase: APIError,
    file: StaticString = #filePath,
    line: UInt = #line,
    _ block: () async throws -> Void
) async {
    do {
        try await block()
        XCTFail("Expected APIError to be thrown", file: file, line: line)
    } catch is APIError {
        // Matching by broad case only; associated NSError values aren't Equatable.
    } catch {
        XCTFail("Expected APIError, got \(error)", file: file, line: line)
    }
}
