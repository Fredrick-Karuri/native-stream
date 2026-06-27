// OnboardingView.swift

import SwiftUI

private enum OnboardingStep {
    case splash, server, playlist, epg
}

struct OnboardingView: View {

    @Environment(SettingsStore.self)          private var settings
    @Environment(PlaylistViewModel.self)      private var playlistVM
    @Environment(ServerHealthViewModel.self)  private var serverHealth
    @Environment(ServerDiscoveryService.self) private var discovery

    @State private var step             = OnboardingStep.splash
    @State private var urlInput         = ""
    @State private var foundEpgURL: URL? = nil

    var onComplete: () -> Void

    var body: some View {
        ZStack {
            NS.bg.ignoresSafeArea()
            ZStack {
                switch step {
                case .splash:
                    SplashStep(onComplete: {
                        urlInput = settings.serverURLString
                        withAnimation { step = .server }
                    })

                case .server:
                    ServerStep(
                        urlInput:        $urlInput,
                        connectionState: serverHealth.connectionState,
                        discovery:       discovery,
                        onConnect:       { url in
                            settings.serverURLString = url
                            Task {
                                await APIClient.shared.setBaseURL(URL(string: url)!)
                                await serverHealth.checkConnection(serverURL: URL(string: url)!)
                            }
                        },
                        onAdvance: {
                            // auto-add server playlist
                            if playlistVM.sources.isEmpty {
                                if let url = settings.serverURL {
                                    playlistVM.addSource(PlaylistSource(
                                        label:           "StreamServer",
                                        url:             url.appendingPathComponent("playlist.m3u"),
                                        refreshInterval: .sixHours
                                    ))
                                }
                            }
                            withAnimation { step = .playlist }
                        },
                        onSkip: {
                            settings.onboardingComplete = true
                            onComplete()
                        }
                    )

                case .playlist:
                    PlaylistStep(
                        connectionState: serverHealth.connectionState,
                        onSourceAdded: { url, label in
                            playlistVM.addSource(PlaylistSource(
                                label:           label.isEmpty ? url.lastPathComponent : label,
                                url:             url,
                                refreshInterval: .sixHours
                            ))
                            Task { await playlistVM.loadAll() }
                        },
                        onAdvance: { epgURL in
                            foundEpgURL = epgURL
                            let success = serverHealth.connectionState.asSuccess
                            let hasEpg  = success?.hasEpg == true
                                       || success?.epgFromPlaylist == true
                                       || epgURL != nil
                            if hasEpg {
                                if let epgURL { settings.epgURLString = epgURL.absoluteString }
                                settings.onboardingComplete = true
                                onComplete()
                            } else {
                                withAnimation { step = .epg }
                            }
                        },
                        onSkip: {
                            let success = serverHealth.connectionState.asSuccess
                            let hasEpg  = success?.hasEpg == true || success?.epgFromPlaylist == true
                            if hasEpg {
                                settings.onboardingComplete = true
                                onComplete()
                            } else {
                                withAnimation { step = .epg }
                            }
                        }
                    )

                case .epg:
                    EPGStep(
                        onSave: { epgURLString in
                            if !epgURLString.isEmpty {
                                settings.epgURLString = epgURLString
                            }
                            settings.onboardingComplete = true
                            onComplete()
                        },
                        onSkip: {
                            settings.onboardingComplete = true
                            onComplete()
                        }
                    )
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal:   .move(edge: .leading).combined(with: .opacity)
            ))
        }
        .onChange(of: discovery.discoveredURL) { _, url in
            guard let url, step == .server else { return }
            urlInput = url.absoluteString
        }
        .onAppear { serverHealth.resetConnectionState() }
    }
}
