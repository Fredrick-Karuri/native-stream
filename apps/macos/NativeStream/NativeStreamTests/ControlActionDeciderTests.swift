// ControlActionDeciderTests
// Unit tests for ControlActionDecider.decide — the pure envelope routing logic
// extracted from ControlViewModel.handle. No ControlSession or PlayerViewModel
// involved; every case is driven entirely by the input Envelope.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class ControlActionDeciderTests: XCTestCase {

    // MARK: .play

    func testPlayEnvelopeProducesPlayActionWithConstructedChannel() throws {
        let envelope = try XCTUnwrap(Envelope.encoding(
            type: .play, from: "controller-1", to: "target-1",
            payload: PlayPayload(channelID: "bbc1", channelName: "BBC One", streamURL: "http://example.com/bbc1.m3u8")
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
        let envelope = try XCTUnwrap(Envelope.encoding(
            type: .play, from: "controller-1", to: "target-1",
            payload: PlayPayload(channelID: "bbc1", channelName: "BBC One", streamURL: "")
        ))

        let action = ControlActionDecider.decide(for: envelope)

        guard case .play(let channel) = action else {
            return XCTFail("Expected .play action, got \(action)")
        }
        XCTAssertEqual(channel.streamURL.absoluteString, "about:blank")
    }

    func testPlayEnvelopeWithWrongPayloadShapeProducesNoAction() {
        let envelope = Envelope(
            type: .play, from: "controller-1", to: "target-1",
            payload: .object(["unexpected": .string("shape")])
        )

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    // MARK: .stop

    func testStopEnvelopeProducesStopAction() {
        let envelope = Envelope(type: .stop, from: "controller-1", to: "target-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .stop)
    }

    // MARK: .volumeSet

    func testVolumeSetEnvelopeProducesSetVolumeAction() throws {
        let envelope = try XCTUnwrap(Envelope.encoding(
            type: .volumeSet, from: "controller-1", to: "target-1",
            payload: VolumeSetPayload(level: 0.6)
        ))

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .setVolume(0.6))
    }

    func testVolumeSetEnvelopeWithWrongPayloadShapeProducesNoAction() {
        let envelope = Envelope(
            type: .volumeSet, from: "controller-1", to: "target-1",
            payload: .object(["unexpected": .string("shape")])
        )

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    // MARK: .sessionList

    func testSessionListEnvelopeProducesUpdateSessionsFilteredToControllersOnly() throws {
        let controllerSession = SessionInfo(
            deviceID: "controller-1", name: "iPhone", kind: .controller,
            channelID: "", channelName: "", streamURL: "",
            playing: false, volume: 1.0, connectedAt: "2026-07-30T12:00:00Z"
        )
        let targetSession = SessionInfo(
            deviceID: "target-1", name: "Mac", kind: .target,
            channelID: "", channelName: "", streamURL: "",
            playing: false, volume: 1.0, connectedAt: "2026-07-30T12:00:00Z"
        )
        let envelope = try XCTUnwrap(Envelope.encoding(
            type: .sessionList, from: "server", to: "target-1",
            payload: SessionListPayload(sessions: [controllerSession, targetSession])
        ))

        let action = ControlActionDecider.decide(for: envelope)

        XCTAssertEqual(action, .updateSessions([controllerSession]))
    }

    func testSessionListEnvelopeWithWrongPayloadShapeProducesNoAction() {
        let envelope = Envelope(
            type: .sessionList, from: "server", to: "target-1",
            payload: .object(["unexpected": .string("shape")])
        )

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    // MARK: .ping

    func testPingEnvelopeProducesSendPongAction() {
        let envelope = Envelope(type: .ping, from: "server", to: "target-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .sendPong)
    }

    // MARK: Unhandled types

    func testRegisterEnvelopeProducesNoAction() {
        let envelope = Envelope(type: .register, from: "target-1", to: "server")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testPullBackEnvelopeProducesNoAction() {
        let envelope = Envelope(type: .pullBack, from: "controller-1", to: "target-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testPullBackAckEnvelopeProducesNoAction() {
        let envelope = Envelope(type: .pullBackAck, from: "target-1", to: "controller-1")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testPongEnvelopeProducesNoAction() {
        let envelope = Envelope(type: .pong, from: "target-1", to: "server")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }

    func testStateUpdateEnvelopeProducesNoAction() {
        let envelope = Envelope(type: .stateUpdate, from: "target-1", to: "broadcast")

        XCTAssertEqual(ControlActionDecider.decide(for: envelope), .none)
    }
}
