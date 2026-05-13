// NativeStreamApp.swift — FX-003, FX-007
// Single source of environment injection. No loading here — AppShell owns that.

import SwiftUI

@main
struct NativeStreamApp: App {

    @State private var playlistVM     = PlaylistViewModel()
    @State private var epgVM          = EPGViewModel()
    @State private var playerVM       = PlayerViewModel()
    @State private var settings       = SettingsStore()
    @State private var favourites     = FavouritesManager()
    @State private var serverHealth   = ServerHealthViewModel()
    @State private var channelManager = ChannelManagerViewModel()

    var body: some Scene {
        WindowGroup {
            Group {
                if !settings.onboardingComplete {
                    OnboardingView { settings.onboardingComplete = true }
                        .environment(settings)
                        .environment(serverHealth)
                        .environment(playlistVM)
                } else {
                    AppShell()
                        .environment(playlistVM)
                        .environment(epgVM)
                        .environment(playerVM)
                        .environment(settings)
                        .environment(favourites)
                        .environment(serverHealth)
                        .environment(channelManager)
                }
            }
            // ── No .task here — AppShell.loadAll() owns startup loading ──
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)

        // macOS ⌘, shortcut still works via Settings scene
        Settings {
            SettingsScreen()
                .environment(settings)
                .environment(playlistVM)
                .environment(serverHealth)
                .environment(channelManager)
        }
    }
}
