// File:Keyboard.swift


import SwiftUI
import AppKit

extension AppShell {
    /// Separate coordinator to isolate low-level macOS hardware event processing from view layout code.
    final class GlobalKeyMonitor {
        private var monitor: Any?

        /// Active event routing configurations
        struct Configuration {
            let showPlayer: Bool
            let destination: AppDestination
            let hasSheetOpen: Bool
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
                
                // 1. Command Shield: Immediately let system operations or multi-key shortcuts (like ⌘F, ⌘C, ⌘V) pass through unimpeded
                if event.modifierFlags.contains(.command) {
                    return event
                }

                // 2. Focused Input Shield: If an active field has typing priority, let single-letter strokes pass through
                if self.isInteractionLayerActive() {
                    return event
                }

                let chars = event.charactersIgnoringModifiers ?? ""

                // 3. Escape Pipeline (Keycode 53)
                if event.keyCode == 53 {
                    if config.showPlayer {
                        config.onClosePlayer()
                        return nil
                    }
                    if config.destination != .now && !config.hasSheetOpen {
                        config.onGoHome()
                        return nil
                    }
                }

                // 4. Universal Full Screen Toggle
                if chars == "f" || chars == "F" {
                    NSApp.mainWindow?.toggleFullScreen(nil)
                    return nil
                }

                // 5. Media Specific Hotkeys
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

        func stop() {
            if let activeMonitor = monitor {
                NSEvent.removeMonitor(activeMonitor)
                monitor = nil
            }
        }

        private func isInteractionLayerActive() -> Bool {
            // 1. Core Window Layer Check: If a native sheet is actively attached to the window, stop global shortcuts
            if NSApp.mainWindow?.attachedSheet != nil {
                return true
            }
            
            // 2. Text Field Focus Check: If typing inside a text field, preserve character routing
            if let responder = NSApp.mainWindow?.firstResponder {
                let name = String(describing: type(of: responder))
                if name.contains("NSText") || name.contains("Field") {
                    return true
                }
            }
            
            return false
        }


    }
}
