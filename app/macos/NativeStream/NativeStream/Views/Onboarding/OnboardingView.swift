// OnboardingView.swift — FX-016
import SwiftUI

enum OnboardingStep {
    case serverCheck
    case channelSetup
    case epgSetup
    case complete
}

struct OnboardingView: View {

    @Environment(SettingsStore.self)         private var settings
    @Environment(PlaylistViewModel.self)     private var playlistVM
    @Environment(ServerHealthViewModel.self) private var serverHealth
    @Environment(ServerDiscoveryService.self) private var discovery


    @State private var step: OnboardingStep = .serverCheck
    @State private var isChecking = false
    @State private var playlistURLInput = ""
    @State private var epgURLInput = ""

    var onComplete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ForEach(0..<3) { i in
                    Capsule()
                        .fill(stepIndex >= i ? NS.accent : NS.border2)
                        .frame(width: stepIndex == i ? 24 : 8, height: 6)
                        .animation(.spring(response: 0.3), value: stepIndex)
                }
            }
            .padding(.top, NS.Spacing.xxl)

            Spacer()

            Group {
                switch step {
                case .serverCheck:  serverCheckStep
                case .channelSetup: channelSetupStep
                case .epgSetup:     epgSetupStep
                case .complete:     completeStep
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal:   .move(edge: .leading).combined(with: .opacity)
            ))
            .animation(.easeInOut(duration: 0.3), value: step)

            Spacer()
        }
        .frame(width: 500, height: 440)
        .background(NS.bg)
        .onAppear { discovery.scan() }
        .onChange(of: discovery.discoveredURL) { _, url in
            guard let url else { return }
            settings.confirmDiscoveredURL(url)
        }
    }

    // MARK: - Step 1: Server check

    private var serverCheckStep: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Welcome to NativeStream")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            if let found = discovery.discoveredURL {
                // Server found via mDNS
                Text("Server found on your network!")
                    .font(NS.Font.body)
                    .foregroundStyle(NS.text3)

                Text(found.absoluteString)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.accent)

                HStack(spacing: NS.Spacing.md) {
                    Button("Enter manually") { withAnimation { step = .channelSetup } }
                        .buttonStyle(.bordered)

                    Button("Use this server") {
                        settings.confirmDiscoveredURL(found)
                        withAnimation { step = .channelSetup }
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else {
                // Scanning / manual fallback
                Text(discovery.isScanning
                     ? "Scanning your network for NativeStream server…\nOr enter the server URL manually."
                     : "Make sure StreamServer is running.\nOr enter the server URL manually.")
                    .font(NS.Font.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(NS.text3)

                if discovery.isScanning {
                    ProgressView().controlSize(.small)
                }

                NSCodeBlock(code: "make run-server")

                HStack(spacing: NS.Spacing.md) {
                    Button("Skip") { withAnimation { step = .channelSetup } }
                        .buttonStyle(.bordered)

                    Button(isChecking ? "Checking…" : "Check Connection") {
                        Task { await checkServer() }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isChecking)
                }
            }
        }
        .padding(NS.Spacing.xxl)
    }

    // MARK: - Step 2: Channel setup (FX-016: inline URL entry)

    private var channelSetupStep: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "list.bullet.rectangle")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Add a Playlist Source")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            Text("Paste your M3U playlist URL below.\nThis is usually your StreamServer's playlist endpoint.")
                .font(NS.Font.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(NS.text3)

            TextField("http://localhost:8888/playlist.m3u", text: $playlistURLInput)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 340)

            HStack(spacing: NS.Spacing.md) {
                Button("Skip") { withAnimation { step = .epgSetup } }
                    .buttonStyle(.bordered)

                Button("Add & Continue") {
                    addPlaylistSource()
                    withAnimation { step = .epgSetup }
                }
                .buttonStyle(.borderedProminent)
                .disabled(playlistURLInput.isEmpty)
            }
        }
        .padding(NS.Spacing.xxl)
    }

    // MARK: - Step 3: EPG setup

    private var epgSetupStep: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "tv.fill")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Set Up TV Guide")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            Text("Enter your EPG URL so NativeStream can show\nwhat's on and upcoming match times.")
                .font(NS.Font.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(NS.text3)

            TextField("http://localhost:8888/epg.xml", text: $epgURLInput)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 340)

            Text("Or use a public source like https://iptv-org.github.io/epg/")
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)

            HStack(spacing: NS.Spacing.md) {
                Button("Skip") { withAnimation { step = .complete } }
                    .buttonStyle(.bordered)

                Button("Save & Finish") {
                    if !epgURLInput.isEmpty {
                        settings.epgURLString = epgURLInput
                    }
                    withAnimation { step = .complete }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(NS.Spacing.xxl)
    }

    // MARK: - Step 4: Complete

    private var completeStep: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(NS.green)

            Text("You're all set!")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            Text("NativeStream is ready. Your channels are loading now.\nSelect any channel to start watching.")
                .font(NS.Font.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(NS.text3)

            Button("Start Watching") {
                settings.onboardingComplete = true
                onComplete()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(NS.Spacing.xxl)
    }

    // MARK: - Helpers

    private var stepIndex: Int {
        switch step {
        case .serverCheck:  return 0
        case .channelSetup: return 1
        case .epgSetup:     return 2
        case .complete:     return 2
        }
    }

    private func checkServer() async {
        isChecking = true
        defer { isChecking = false }
        guard let url = settings.serverURL else { return }
        await serverHealth.check(serverURL: url)
        if serverHealth.isConnected {
            // Auto-add server playlist if no sources configured
            if playlistVM.sources.isEmpty {
                let serverPlaylist = url.appendingPathComponent("playlist.m3u")
                playlistVM.addSource(PlaylistSource(
                    label: "StreamServer",
                    url: serverPlaylist,
                    refreshInterval: .sixHours
                ))
            }
            withAnimation { step = .channelSetup }
        }
    }

    private func addPlaylistSource() {
        guard let url = URL(string: playlistURLInput.trimmingCharacters(in: .whitespaces)),
              url.scheme != nil else { return }
        let label = url.host ?? "Playlist"
        playlistVM.addSource(PlaylistSource(
            label: label,
            url: url,
            refreshInterval: .sixHours
        ))
        // FX-016: trigger load immediately so channels appear on complete screen
        Task { await playlistVM.loadAll() }
    }
}
