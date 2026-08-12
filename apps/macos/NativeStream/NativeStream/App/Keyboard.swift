// File:Keyboard.swift

import SwiftUI
import AppKit

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

            let input = KeyInput(
                keyCode: event.keyCode,
                charactersIgnoringModifiers: event.charactersIgnoringModifiers ?? "",
                commandKeyPressed: event.modifierFlags.contains(.command),
                isInteractionLayerActive: self.isInteractionLayerActive(),
                isNativeFullScreenActive: NSApp.mainWindow?.styleMask.contains(.fullScreen) ?? false
            )

            switch Self.decideAction(for: input, config: config) {
            case .closePlayer:
                config.onClosePlayer()
                return nil
            case .goHome:
                config.onGoHome()
                return nil
            case .toggleSidebar:
                config.onToggleSidebar()
                return nil
            case .toggleNativeFullScreen:
                NSApp.mainWindow?.toggleFullScreen(nil)
                return nil
            case .togglePlayback:
                config.onPlaybackToggle()
                return nil
            case .toggleMute:
                config.onMuteToggle()
                return nil
            case .togglePiP:
                config.onPiPToggle()
                return nil
            case .passThrough:
                return event
            }
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
