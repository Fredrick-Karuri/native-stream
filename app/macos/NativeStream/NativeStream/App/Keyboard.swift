// File:Keyboard.swift


import SwiftUI
import AppKit


extension AppShell {
    final class GlobalKeyMonitor {
        private var monitor: Any?

        struct Configuration {
            let showPlayer: Bool
            let destination: AppDestination
            let hasSheetOpen: Bool
            let isPlayerSidebarOpen: Bool
            let onToggleSidebar: () -> Void
            let onClosePlayer: () -> Void
            let onGoHome: () -> Void
            let onPlaybackToggle: () -> Void
            let onMuteToggle: () -> Void
            let onPiPToggle: () -> Void
        }

        func start(with config: Configuration) {
            stop()

            monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
                guard let self = self else { return event }
                
                if event.modifierFlags.contains(.command) { return event }
                if self.isInteractionLayerActive() { return event }

                let chars = event.charactersIgnoringModifiers ?? ""

                // Escape Pipeline (Keycode 53)
                if event.keyCode == 53 {
                    if config.showPlayer {
                        config.onClosePlayer()
                        return nil
                    }
                    if config.destination != .now {
                        config.onGoHome()
                        return nil
                    }
                }

                // ── Current: Replace With Contextual 'F' Hierarchy ─────────────
                if chars == "f" || chars == "F" {
                    if config.showPlayer {
                        // Priority 1: Collapse the layout sidebar if it's visible
                        if config.isPlayerSidebarOpen {
                            config.onToggleSidebar()
                            return nil
                        }
                        
                        // Priority 2: Handle native OS full-screen toggles when sidebar is already closed
                        let isNativeFS = NSApp.mainWindow?.styleMask.contains(.fullScreen) ?? false
                        if isNativeFS {
                            // If already in native full-screen, exit it first!
                            NSApp.mainWindow?.toggleFullScreen(nil)
                            return nil
                        } else {
                            // If not in full-screen, enter it!
                            NSApp.mainWindow?.toggleFullScreen(nil)
                            return nil
                        }

                    } else {
                        // Standard global full-screen behavior for non-player screens
                        NSApp.mainWindow?.toggleFullScreen(nil)
                        return nil
                    }
                }
                // ─────────────────────────────────────────────────────────────

                if config.showPlayer {
                    switch chars {
                    case " ": config.onPlaybackToggle(); return nil
                    case "m", "M": config.onMuteToggle(); return nil
                    case "p", "P": config.onPiPToggle(); return nil
                    default: break
                    }
                }

                return event
            }
        }

        private func isInteractionLayerActive() -> Bool {
            if NSApp.mainWindow?.attachedSheet != nil { return true }
            if let responder = NSApp.mainWindow?.firstResponder {
                let name = String(describing: type(of: responder))
                if name.contains("NSText") || name.contains("Field") { return true }
            }
            return false
        }

        func stop() {
            if let activeMonitor = monitor {
                NSEvent.removeMonitor(activeMonitor)
                monitor = nil
            }
        }
    }
}

