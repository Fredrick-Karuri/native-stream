// ControlActionDeciderTests
// Unit tests for ControlActionDecider.decide — the pure envelope routing logic
// extracted from ControlViewModel.handle. No ControlSession or PlayerViewModel
// involved; every case is driven entirely by the input Envelope.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
import SwiftProtobuf
import SdkGenSwift

@testable import NativeStream

final class ControlActionDeciderTests: XCTestCase {

    // MARK: Helpers

    private func envelope(type: Stream_V1_MessageType, from: String, to: String) -> Stream_V1_Envelope {
        var e = Stream_V1_Envelope()
        e.type = type
        e.from = from
        e.to = to
        return e
    }

    // MARK: .play

    func testPlayEnvelopeProducesPlayActionWithConstructedChannel() throws {
        var payload = Stream_V1_PlayPayload()
        payload.channelID = "bbc1"
        payload.channelName = "BBC One"
        payload.streamURL = "http://example.com/bbc1.m3u8"

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .play, from: "controller-1", to: "target-1", payload: payload
        ))

        let action = ControlActionDecider.decide(for: envelope)

        guard case .play(let channel) = action else {
            return XCTFail("Expected .play action, got \(action)")
        }
        XCTAssertEqual(channel.name, "BBC One")
        XCTAssertEqual(channel.groupTitle, "Remote")
        XCTAssertEqual(channel.tvgId, "")
        XCTAssertEqual(channel.streamURL.absoluteString, "http://example.com/bbc1.m3u8")
    }

    func testPlayEnvelopeWithUnparsableStreamURLFallsBackToAboutBlank() throws {
        var payload = Stream_V1_PlayPayload()
        payload.channelID = "bbc1"
        payload.channelName = "BBC One"
        payload.streamURL = ""

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .play, from: "controller-1", to: "target-1", payload: payload
        ))

        let action = ControlActionDecider.decide(for: envelope)

        guard case .play(let channel) = action else {
            return XCTFail("Expected .play action, got \(action)")
        }
        XCTAssertEqual(channel.streamURL.absoluteString, "about:blank")
    }

    func testPlayEnvelopeWithMismatchedPayloadFieldsStillDecodesWithDefaults() throws {
        // Protobuf JSON decoding is permissive across message types: unknown
        // fields are ignored and missing fields default rather than erroring.
        // A StateUpdatePayload tagged as .play therefore does NOT fail to
        // decode — it produces a PlayPayload with all-default (empty) fields.
        // This documents current behavior; ControlActionDecider does not
        // validate payload contents beyond structural JSON decoding.
        var wrongPayload = Stream_V1_StateUpdatePayload()
        wrongPayload.channelID = "x"

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .play, from: "controller-1", to: "target-1", payload: wrongPayload
        ))

        guard case .play(let channel) = ControlActionDecider.decide(for: envelope) else {
            return XCTFail("Expected .play action with default-valued channel")
        }
        XCTAssertEqual(channel.name, "")
        XCTAssertEqual(channel.streamURL.absoluteString, "about:blank")
    }
    // MARK: .stop

    func testStopEnvelopeProducesStopAction() {
        let envelope = envelope(type: .stop, from: "controller-1", to: "target-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .stop)
    }

    // MARK: .volumeSet

    func testVolumeSetEnvelopeProducesSetVolumeAction() throws {
        var payload = Stream_V1_VolumeSetPayload()
        payload.level = 0.6

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .volumeSet, from: "controller-1", to: "target-1", payload: payload
        ))

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .setVolume(0.6))
    }

    func testVolumeSetEnvelopeWithWrongPayloadShapeProducesNoAction() throws {
        var wrongPayload = Stream_V1_StateUpdatePayload()
        wrongPayload.channelID = "x"

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .volumeSet, from: "controller-1", to: "target-1", payload: wrongPayload
        ))

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    // MARK: .sessionList

    func testSessionListEnvelopeProducesUpdateSessionsFilteredToControllersOnly() throws {
        var controllerSession = Stream_V1_SessionInfo()
        controllerSession.deviceID = "controller-1"
        controllerSession.name = "iPhone"
        controllerSession.kind = .controller
        controllerSession.volume = 1.0

        var targetSession = Stream_V1_SessionInfo()
        targetSession.deviceID = "target-1"
        targetSession.name = "Mac"
        targetSession.kind = .target
        targetSession.volume = 1.0

        var payload = Stream_V1_SessionListPayload()
        payload.sessions = [controllerSession, targetSession]

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .sessionList, from: "server", to: "target-1", payload: payload
        ))

        let action = ControlActionDecider.decide(for: envelope)

        XCTAssertEqual(action, .updateSessions([controllerSession]))
    }

    func testSessionListEnvelopeWithWrongPayloadShapeProducesNoAction() throws {
        var wrongPayload = Stream_V1_PlayPayload()
        wrongPayload.channelID = "x"

        let envelope = try XCTUnwrap(Stream_V1_Envelope.encoding(
            type: .sessionList, from: "server", to: "target-1", payload: wrongPayload
        ))

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    // MARK: .ping

    func testPingEnvelopeProducesSendPongAction() {
        let envelope = envelope(type: .ping, from: "server", to: "target-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .sendPong)
    }

    // MARK: Unhandled types

    func testRegisterEnvelopeProducesNoAction() {
        let envelope = envelope(type: .register, from: "target-1", to: "server")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testPullBackEnvelopeProducesNoAction() {
        let envelope = envelope(type: .pullBack, from: "controller-1", to: "target-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testPullBackAckEnvelopeProducesNoAction() {
        let envelope = envelope(type: .pullBackAck, from: "target-1", to: "controller-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testPongEnvelopeProducesNoAction() {
        let envelope = envelope(type: .pong, from: "target-1", to: "server")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testStateUpdateEnvelopeProducesNoAction() {
        let envelope = envelope(type: .stateUpdate, from: "target-1", to: "broadcast")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }
}
