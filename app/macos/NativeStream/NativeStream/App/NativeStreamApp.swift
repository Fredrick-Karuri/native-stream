// NativeStreamApp.swift — Phase 4: wires all services including MediaKeyHandler,
// MenuBarManager, FavouritesManager, and EPG grid.

import SwiftUI
import AVFoundation

@main
struct NativeStreamApp: App {

    @State private var playlistVM   = PlaylistViewModel()
    @State private var epgVM        = EPGViewModel()
    @State private var playerVM     = PlayerViewModel()
    @State private var settings     = SettingsStore()
    @State private var favourites   = FavouritesManager()
    @State private var serverHealth = ServerHealthViewModel()


    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(playlistVM)
                .environment(epgVM)
                .environment(playerVM)
                .environment(settings)
                .environment(favourites)
                .environment(serverHealth)
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
            CommandMenu("Playback") {
                Button("Play / Pause") { playerVM.togglePlayback() }
                    .keyboardShortcut(.space, modifiers: [])
                Divider()
                Button("Enter Picture in Picture") { playerVM.enterPiP() }
                    .keyboardShortcut("p", modifiers: [.command, .shift])
                Divider()
                Button("Refresh Playlist") { Task { await playlistVM.loadAll() } }
                    .keyboardShortcut("r", modifiers: .command)
            }
        }

        Settings {
            SettingsView()
                .environment(settings)
                .environment(playlistVM)
        }
    }

    init() {
    }
}