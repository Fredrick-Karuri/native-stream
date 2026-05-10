// NativeStreamApp.swift — Phase 4 + UX v4
// Wires AppShell as root view with all environment objects.

import SwiftUI

@main
struct NativeStreamApp: App {

    @State private var playlistVM   = PlaylistViewModel()
    @State private var epgVM        = EPGViewModel()
    @State private var playerVM     = PlayerViewModel()
    @State private var settings     = SettingsStore()
    @State private var favourites   = FavouritesManager()
    @State private var serverHealth = ServerHealthViewModel()
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
            .task {
                guard let url = settings.serverURL else { return }
                await serverHealth.check(serverURL: url)
                epgVM.epgURL = settings.epgURL
                async let _ = playlistVM.loadAll()
                async let _ = epgVM.load()
                playlistVM.scheduleAutoRefresh()
                serverHealth.startPolling(serverURL: url)
            }
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)

        Settings {
            SettingsScreen()
                .environment(settings)
                .environment(playlistVM)
                .environment(serverHealth)
                .environment(channelManager)
        }
    }
}
