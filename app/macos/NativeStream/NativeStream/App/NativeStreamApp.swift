// NativeStreamApp.swift
// Single source of environment injection. No loading here — AppShell owns that.

import SwiftUI

@main
struct NativeStreamApp: App {

    
    @State private var epgVM          = EPGViewModel()
    @State private var playerVM       = PlayerViewModel()
    @State private var favourites     = FavouritesManager()
    @State private var serverHealth   = ServerHealthViewModel()
    @State private var discoveryService = ServerDiscoveryService()
    @State private var channelManager = ChannelManagerViewModel()
    @State private var controlVM = ControlViewModel(controlSession: ControlSession())
    @State private var playlistVM: PlaylistViewModel
    @State private var settings: SettingsStore
    
    init() {
        let s = SettingsStore()
        _settings    = State(initialValue: s)
        _playlistVM  = State(initialValue: PlaylistViewModel(settings: s))
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if !settings.onboardingComplete {
                    OnboardingView { }
                        .environment(settings)
                        .environment(serverHealth)
                        .environment(playlistVM)
                        .environment(discoveryService)
                        .onAppear { serverHealth.resetConnectionState() }

                } else {
                    AppShell()
                        .environment(playlistVM)
                        .environment(epgVM)
                        .environment(playerVM)
                        .environment(settings)
                        .environment(favourites)
                        .environment(serverHealth)
                        .environment(channelManager)
                        .environment(discoveryService)
                        .environment(controlVM)
                        .task {
                            guard let url = settings.serverURL else { return }
                            controlVM.start(
                                serverURL: url,
                                deviceID: settings.controlDeviceID,
                                playerVM: playerVM
                            )
                        }
                }
            }
            
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
                .environment(discoveryService)
        }
    }
}
