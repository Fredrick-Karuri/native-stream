// NativeStreamApp.swift
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
        .commands {
            CommandGroup(after: .newItem) {
                Button("Open Stream…") {
                    NotificationCenter.default.post(name: .openPlayURL, object: nil)
                }
                .keyboardShortcut("u", modifiers: .command)
            }
            CommandGroup(replacing: .help) {
                Button("NativeStream Help") {
                    NotificationCenter.default.post(name: .showHelp, object: nil)
                }
                .keyboardShortcut("?", modifiers: .command)
            }
        }

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
