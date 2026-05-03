// NativeStreamApp.swift — @main entry point
// NS-002 + NS-051

import SwiftUI
import AVFoundation

@main
struct NativeStreamApp: App {

    @State private var playlistVM  = PlaylistViewModel()
    @State private var epgVM       = EPGViewModel()
    @State private var playerVM    = PlayerViewModel()
    @State private var settings    = SettingsStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(playlistVM)
                .environment(epgVM)
                .environment(playerVM)
                .environment(settings)
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)
        .commands {
            CommandGroup(replacing: .appSettings) {
                Button("Settings…") {
                    NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
                }
                .keyboardShortcut(",", modifiers: .command)
            }
        }

        Settings {
            SettingsView()
                .environment(settings)
                .environment(playlistVM)
        }
    }

    init() {
        // NS-047: Configure background audio session
        configureAudioSession()
        NowPlayingService.shared.configure()
    }

    private func configureAudioSession() {
        // macOS uses AVAudioSession differently; for HLS via AVFoundation
        // background audio is handled via the entitlement + keeping AVPlayer active.
        // Nothing to configure on macOS directly — handled by system.
    }
}