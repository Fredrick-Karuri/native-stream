// KeyDecisionTests
// Unit tests for GlobalKeyMonitor.decideAction — the pure routing logic
// extracted from the NSEvent key monitor. No NSApp/NSEvent involved.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class KeyDecisionTests: XCTestCase {

    private func makeConfig(
        showPlayer: Bool = false,
        destination: AppDestination = .now,
        hasSheetOpen: Bool = false,
        isPlayerSidebarOpen: Bool = false
    ) -> GlobalKeyMonitor.Configuration {
        .init(
            showPlayer: showPlayer,
            destination: destination,
            hasSheetOpen: hasSheetOpen,
            isPlayerSidebarOpen: isPlayerSidebarOpen,
            onToggleSidebar: {},
            onClosePlayer: {},
            onGoHome: {},
            onPlaybackToggle: {},
            onMuteToggle: {},
            onPiPToggle: {}
        )
    }

    private func makeInput(
        keyCode: UInt16 = 0,
        chars: String = "",
        commandKeyPressed: Bool = false,
        isInteractionLayerActive: Bool = false,
        isNativeFullScreenActive: Bool = false
    ) -> GlobalKeyMonitor.KeyInput {
        .init(
            keyCode: keyCode,
            charactersIgnoringModifiers: chars,
            commandKeyPressed: commandKeyPressed,
            isInteractionLayerActive: isInteractionLayerActive,
            isNativeFullScreenActive: isNativeFullScreenActive
        )
    }

    // MARK: Command modifier and interaction layer short-circuits

    func testCommandModifiedKeyAlwaysPassesThrough() {
        let input = makeInput(keyCode: GlobalKeyMonitor.escapeKeyCode, commandKeyPressed: true)
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .passThrough)
    }

    func testActiveInteractionLayerPassesThroughEvenForEscape() {
        let input = makeInput(keyCode: GlobalKeyMonitor.escapeKeyCode, isInteractionLayerActive: true)
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .passThrough)
    }

    func testActiveInteractionLayerPassesThroughEvenForFKey() {
        let input = makeInput(chars: "f", isInteractionLayerActive: true)
        let config = makeConfig(showPlayer: false)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .passThrough)
    }

    // MARK: Escape

    func testEscapeClosesPlayerWhenPlayerIsShowing() {
        let input = makeInput(keyCode: GlobalKeyMonitor.escapeKeyCode)
        let config = makeConfig(showPlayer: true, destination: .allChannels)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .closePlayer)
    }

    func testEscapeGoesHomeWhenPlayerHiddenAndNotAlreadyHome() {
        let input = makeInput(keyCode: GlobalKeyMonitor.escapeKeyCode)
        let config = makeConfig(showPlayer: false, destination: .allChannels)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .goHome)
    }

    func testEscapePassesThroughWhenPlayerHiddenAndAlreadyHome() {
        let input = makeInput(keyCode: GlobalKeyMonitor.escapeKeyCode)
        let config = makeConfig(showPlayer: false, destination: .now)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .passThrough)
    }

    // MARK: 'F' hierarchy

    func testFTogglesSidebarWhenPlayerShowingAndSidebarOpen() {
        let input = makeInput(chars: "f")
        let config = makeConfig(showPlayer: true, isPlayerSidebarOpen: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleSidebar)
    }

    func testUppercaseFTogglesSidebarWhenPlayerShowingAndSidebarOpen() {
        let input = makeInput(chars: "F")
        let config = makeConfig(showPlayer: true, isPlayerSidebarOpen: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleSidebar)
    }

    func testFTogglesNativeFullScreenWhenPlayerShowingAndSidebarClosedAndNotFullScreen() {
        let input = makeInput(chars: "f", isNativeFullScreenActive: false)
        let config = makeConfig(showPlayer: true, isPlayerSidebarOpen: false)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleNativeFullScreen)
    }

    func testFTogglesNativeFullScreenWhenPlayerShowingAndSidebarClosedAndAlreadyFullScreen() {
        let input = makeInput(chars: "f", isNativeFullScreenActive: true)
        let config = makeConfig(showPlayer: true, isPlayerSidebarOpen: false)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleNativeFullScreen)
    }

    func testFTogglesNativeFullScreenWhenPlayerNotShowing() {
        let input = makeInput(chars: "f")
        let config = makeConfig(showPlayer: false)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleNativeFullScreen)
    }

    // MARK: Player-only shortcuts

    func testSpaceTogglesPlaybackWhenPlayerShowing() {
        let input = makeInput(chars: " ")
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .togglePlayback)
    }

    func testSpacePassesThroughWhenPlayerHidden() {
        let input = makeInput(chars: " ")
        let config = makeConfig(showPlayer: false)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .passThrough)
    }

    func testLowercaseMTogglesMuteWhenPlayerShowing() {
        let input = makeInput(chars: "m")
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleMute)
    }

    func testUppercaseMTogglesMuteWhenPlayerShowing() {
        let input = makeInput(chars: "M")
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .toggleMute)
    }

    func testLowercasePTogglesPiPWhenPlayerShowing() {
        let input = makeInput(chars: "p")
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .togglePiP)
    }

    func testUppercasePTogglesPiPWhenPlayerShowing() {
        let input = makeInput(chars: "P")
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .togglePiP)
    }

    func testUnhandledCharacterPassesThroughWhenPlayerShowing() {
        let input = makeInput(chars: "z")
        let config = makeConfig(showPlayer: true)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: input, config: config), .passThrough)
    }

    func testPlayerOnlyShortcutsPassThroughWhenPlayerHidden() {
        let config = makeConfig(showPlayer: false)

        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: makeInput(chars: "m"), config: config), .passThrough)
        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: makeInput(chars: "p"), config: config), .passThrough)
        XCTAssertEqual(GlobalKeyMonitor.decideAction(for: makeInput(chars: " "), config: config), .passThrough)
    }
}
