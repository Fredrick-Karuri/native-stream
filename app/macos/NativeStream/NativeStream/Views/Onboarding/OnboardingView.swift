// OnboardingView.swift

import SwiftUI

private enum OnboardingStep {
    case splash, server, playlist, epg
}

private let iptvOrgEPG = "https://iptv-org.github.io/epg/guides/en/xmltv.xml"

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
                        onSourceAdded:   { url in
                            playlistVM.addSource(PlaylistSource(
                                label:           url.host ?? "Playlist",
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
        .frame(width: 560, height: 480)
        .onChange(of: serverHealth.connectionState) { _, state in
            if case .success = state, step == .server {
                withAnimation { step = .playlist }
            }
        }
        .onChange(of: discovery.discoveredURL) { _, url in
            guard let url, step == .server else { return }
            urlInput = url.absoluteString
        }
        .onAppear { serverHealth.resetConnectionState() }
    }
}

// MARK: - Splash

private struct SplashStep: View {
    let onComplete: () -> Void
    @State private var opacity = 0.0

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "play.tv.fill")
                .font(.system(size: 56))
                .foregroundStyle(NS.accent)
            Text("NativeStream")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)
            Text("Your live TV. On every screen.")
                .font(NS.Font.body)
                .foregroundStyle(NS.text3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
        .opacity(opacity)
        .task {
            withAnimation(.easeIn(duration: 0.4)) { opacity = 1.0 }
            try? await Task.sleep(for: .seconds(2))
            onComplete()
        }
    }
}

// MARK: - Server

private struct ServerStep: View {
    @Binding var urlInput: String
    let connectionState: OnboardingConnectionState
    let discovery: ServerDiscoveryService
    let onConnect: (String) -> Void
    let onAdvance: () -> Void
    let onSkip: () -> Void

    @State private var showServer   = false
    @State private var showPlaylist = false
    @State private var showEpg      = false

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Connect to your server")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            switch connectionState {
            case .checking, .success:
                narrativeProgress

            case .failure(let reason):
                failureState(reason: reason)

            case .idle:
                idleInput
            }
        }
        .padding(NS.Spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
        .onChange(of: connectionState) { _, state in
            if case .success(_, _, let hasEpg, _) = state {
                Task {
                    withAnimation { showServer = true }
                    try? await Task.sleep(for: .milliseconds(300))
                    withAnimation { showPlaylist = true }
                    try? await Task.sleep(for: .milliseconds(300))
                    if hasEpg { withAnimation { showEpg = true } }
                    try? await Task.sleep(for: .milliseconds(600))
                    onAdvance()
                }
            }
        }
    }

    private var idleInput: some View {
        VStack(spacing: NS.Spacing.lg) {
            if discovery.isScanning && discovery.discoveredURL == nil {
                HStack(spacing: NS.Spacing.sm) {
                    ProgressView().controlSize(.small)
                    Text("Scanning your network…")
                        .font(NS.Font.body)
                        .foregroundStyle(NS.text3)
                }
            } else if let found = discovery.discoveredURL {
                Text("Server found on your network!")
                    .font(NS.Font.body)
                    .foregroundStyle(NS.accent)
            } else {
                Text("Enter your NativeStream server address.")
                    .font(NS.Font.body)
                    .foregroundStyle(NS.text3)
            }

            NSTextField(placeholder: "http://192.168.1.42:8888", text: $urlInput)
                .frame(maxWidth: 340)

            NSCodeBlock(code: "make run-server")

            HStack(spacing: NS.Spacing.md) {
                if discovery.discoveredURL == nil {
                    Button(discovery.isScanning ? "Scanning…" : "Scan Network") {
                        discovery.scan()
                    }
                    .buttonStyle(.bordered)
                    .disabled(discovery.isScanning)
                }
                Button("Connect") {
                    onConnect(urlInput)
                }
                .buttonStyle(.borderedProminent)
                .disabled(urlInput.isEmpty)
            }

            Button("Skip for now") { onSkip() }
                .buttonStyle(.plain)
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3.opacity(0.5))
        }
    }

    private var narrativeProgress: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.sm) {
            if showServer {
                Text("✓ Server reached")
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.accent)
                    .transition(.opacity)
            }
            if showPlaylist {
                let channels = connectionState.asSuccess?.channels ?? 0
                Text("✓ Playlist found — \(channels) channels")
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.accent)
                    .transition(.opacity)
            }
            if showEpg {
                Text("✓ TV Guide found")
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.accent)
                    .transition(.opacity)
            }
            if case .checking = connectionState {
                ProgressView().controlSize(.small)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func failureState(reason: FailureReason) -> some View {
        VStack(alignment: .leading, spacing: NS.Spacing.sm) {
            Text("✗ Couldn't reach \(urlInput)")
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.red)

            let suggestions: [String] = {
                switch reason {
                case .unreachable: return [
                    "Is the server running? Try: make run-server",
                    "Are you on the same WiFi network?",
                    "Check the IP in your server's terminal output",
                ]
                case .noPlaylist: return [
                    "Server reached but no playlist found",
                    "Check StreamServer is running: make run-server",
                ]
                case .unknown: return [
                    "Something went wrong — check the server logs",
                ]
                }
            }()

            ForEach(suggestions, id: \.self) { s in
                Text("→ \(s)")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
            }

            Button("Try again") { onConnect(urlInput) }
                .buttonStyle(.borderedProminent)
                .padding(.top, NS.Spacing.sm)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Playlist

private struct PlaylistStep: View {
    let connectionState: OnboardingConnectionState
    let onSourceAdded: (URL) -> Void
    let onAdvance: (URL?) -> Void
    let onSkip: () -> Void

    @State private var playlistInput = ""
    @State private var isProbing     = false
    @State private var foundEpg: URL? = nil
    @State private var isAdded       = false

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "list.bullet.rectangle")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Add a Playlist Source")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            Text("Your server playlist was added automatically.\nWant to add another M3U source?")
                .font(NS.Font.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(NS.text3)

            NSTextField(placeholder: "http://192.168.1.42:8888/playlist.m3u", text: $playlistInput)
                .frame(maxWidth: 340)
                .onChange(of: playlistInput) { _, _ in
                    foundEpg = nil
                    isAdded  = false
                }

            if isAdded {
                VStack(spacing: NS.Spacing.sm) {
                    Text("✓ Playlist added")
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.accent)
                    if let epg = foundEpg {
                        Text("✓ TV Guide found — will be added automatically")
                            .font(NS.Font.captionMed)
                            .foregroundStyle(NS.accent)
                    } else {
                        Text("No TV Guide found in this playlist")
                            .font(NS.Font.monoSm)
                            .foregroundStyle(NS.text3)
                    }
                    Button("Continue") { onAdvance(foundEpg) }
                        .buttonStyle(.borderedProminent)
                }
                .transition(.opacity)
            } else {
                if isProbing {
                    Text("Checking for TV Guide…")
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                }
                HStack(spacing: NS.Spacing.md) {
                    Button("Skip for now") { onSkip() }
                        .buttonStyle(.bordered)
                    Button(isProbing ? "Checking…" : "Add Source") {
                        guard let url = URL(string: playlistInput.trimmingCharacters(in: .whitespaces)),
                              url.scheme != nil else { return }
                        Task {
                            isProbing = true
                            let epg   = await APIClient.shared.probePlaylistForEpg(url: url)
                            foundEpg  = epg
                            isProbing = false
                            isAdded   = true
                            onSourceAdded(url)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(playlistInput.isEmpty || isProbing)
                }
            }
        }
        .padding(NS.Spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
        .animation(.easeInOut(duration: 0.3), value: isAdded)
    }
}

// MARK: - EPG

private struct EPGStep: View {
    let onSave: (String) -> Void
    let onSkip: () -> Void

    @State private var epgInput = ""

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "tv.fill")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Add a TV Guide")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            Text("A TV Guide shows upcoming match times and what's on.\nYour server didn't return one automatically.")
                .font(NS.Font.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(NS.text3)

            NSTextField(placeholder: "http://192.168.1.42:8888/epg.xml", text: $epgInput)
                .frame(maxWidth: 340)

            Button("Use IPTV-org guide") { epgInput = iptvOrgEPG }
                .buttonStyle(.bordered)

            HStack(spacing: NS.Spacing.md) {
                Button("Skip for now") { onSkip() }
                    .buttonStyle(.bordered)
                Button("Add Guide") { onSave(epgInput) }
                    .buttonStyle(.borderedProminent)
                    .disabled(epgInput.isEmpty)
            }
        }
        .padding(NS.Spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
    }
}
