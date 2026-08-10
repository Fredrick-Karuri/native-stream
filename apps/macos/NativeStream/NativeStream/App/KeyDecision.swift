// File: KeyDecision.swift
//
// Pure decision logic extracted from GlobalKeyMonitor. Given a key event's
// raw inputs and the current Configuration, returns what action (if any) should fire,
// with no dependency on NSEvent, NSApp, or any live AppKit event loop. This is what
// makes the routing testable: GlobalKeyMonitor.start() now just asks this function
// what to do and executes the corresponding side effect.

import Foundation

extension GlobalKeyMonitor {

    /// Keycode for the Escape key on macOS, as delivered by NSEvent.keyCode.
    static let escapeKeyCode: UInt16 = 53

    /// The result of evaluating a key press against the current Configuration.
    enum KeyAction: Equatable {
        case closePlayer
        case goHome
        case toggleSidebar
        case toggleNativeFullScreen
        case togglePlayback
        case toggleMute
        case togglePiP
        /// No app-level handling applies; the event should be passed through untouched.
        case passThrough
    }

    /// Inputs needed to decide a KeyAction, mirroring what NSEvent exposes,
    /// plus the two pieces of AppKit state the original closure reads directly.
    struct KeyInput {
        let keyCode: UInt16
        let charactersIgnoringModifiers: String
        let commandKeyPressed: Bool
        let isInteractionLayerActive: Bool
        let isNativeFullScreenActive: Bool
    }

    /// Decides what should happen for a given key input and Configuration.
    /// Mirrors the original closure's branching and short-circuit order exactly:
    /// command-modified keys and active interaction layers pass through first,
    /// then Escape, then the contextual 'F' hierarchy, then player-only shortcuts.
    static func decideAction(for input: KeyInput, config: Configuration) -> KeyAction {
        if input.commandKeyPressed { return .passThrough }
        if input.isInteractionLayerActive { return .passThrough }

        let chars = input.charactersIgnoringModifiers

        if input.keyCode == escapeKeyCode {
            if config.showPlayer { return .closePlayer }
            if config.destination != .now { return .goHome }
        }

        if chars == "f" || chars == "F" {
            if config.showPlayer {
                if config.isPlayerSidebarOpen { return .toggleSidebar }
                return .toggleNativeFullScreen
            }
            return .toggleNativeFullScreen
        }

        if config.showPlayer {
            switch chars {
            case " ": return .togglePlayback
            case "m", "M": return .toggleMute
            case "p", "P": return .togglePiP
            default: break
            }
        }

        return .passThrough
    }
}
