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
    @State private var toastCenter    = ToastCenter()
    @State private var sourceVM: SourceViewModel
    @State private var channelLoadingVM: ChannelLoadingViewModel
    @State private var settings: SettingsStore

    init() {
        let s = SettingsStore()
        let dataStore = SettingsDataStore()
        let source = SourceViewModel(dataStore: dataStore)
        let loading = ChannelLoadingViewModel(
            sourceViewModel: source,
            settings: s,
            repository: ChannelRepositoryImpl(),
            dataStore: dataStore
        )
        source.onAutoRefreshTriggered = { [weak loading] in
            await loading?.loadAll()
        }
        _settings        = State(initialValue: s)
        _sourceVM         = State(initialValue: source)
        _channelLoadingVM = State(initialValue: loading)
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if !settings.onboardingComplete {
                    OnboardingView { }
                        .environment(settings)
                        .environment(serverHealth)
                        .environment(sourceVM)
                        .environment(channelLoadingVM)
                        .environment(discoveryService)
                        .onAppear { serverHealth.resetConnectionState() }

                } else {
                    AppShell()
                        .environment(sourceVM)
                        .environment(channelLoadingVM)
                        .environment(epgVM)
                        .environment(playerVM)
                        .environment(settings)
                        .environment(favourites)
                        .environment(serverHealth)
                        .environment(channelManager)
                        .environment(discoveryService)
                        .environment(controlVM)
                        .environment(toastCenter)
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
                .environment(sourceVM)
                .environment(channelLoadingVM)
                .environment(serverHealth)
                .environment(channelManager)
                .environment(discoveryService)
        }
    }
}
