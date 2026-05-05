// OnboardingView.swift — NS-321
// First-launch onboarding: server check → channel setup → EPG setup.

import SwiftUI

enum OnboardingStep {
    case serverCheck
    case channelSetup
    case epgSetup
    case complete
}

struct OnboardingView: View {

    @Environment(SettingsStore.self)     private var settings
    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(ServerHealthViewModel.self) private var serverHealth

    @State private var step: OnboardingStep = .serverCheck
    @State private var isChecking = false
    @State private var epgURLInput = ""

    var onComplete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Progress indicator
            HStack(spacing: 8) {
                ForEach(0..<3) { i in
                    Capsule()
                        .fill(stepIndex >= i ? Color.accentColor : Color.primary.opacity(0.15))
                        .frame(width: stepIndex == i ? 24 : 8, height: 6)
                        .animation(.spring(response: 0.3), value: stepIndex)
                }
            }
            .padding(.top, 32)

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
        .frame(width: 500, height: 420)
        .background(.background)
    }

    // MARK: - Steps

    private var serverCheckStep: some View {
        VStack(spacing: 20) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(Color.accentColor)

            Text("Welcome to NativeStream")
                .font(.title2.bold())

            Text("First, make sure the StreamServer is running.\nOpen Terminal and run:")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)

            CodeBlock("make run-server")

            Text("Or install as a background service:")
                .foregroundStyle(.secondary)
            CodeBlock("make install-service")

            HStack(spacing: 12) {
                Button("Skip") { withAnimation { step = .channelSetup } }
                    .buttonStyle(.bordered)

                Button(isChecking ? "Checking…" : "Check Connection") {
                    Task { await checkServer() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isChecking)
            }
        }
        .padding(40)
    }

    private var channelSetupStep: some View {
        VStack(spacing: 20) {
            Image(systemName: "list.bullet.rectangle")
                .font(.system(size: 48))
                .foregroundStyle(Color.accentColor)

            Text("Add a Playlist Source")
                .font(.title2.bold())

            Text("Point NativeStream at your stream server's playlist endpoint,\nor paste any M3U URL.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)

            CodeBlock("http://localhost:8888/playlist.m3u")

            Text("Go to Settings → Sources → Add Source to configure this.\nYou can also do it now and come back.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            HStack(spacing: 12) {
                Button("Open Settings") {
                    NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
                }
                .buttonStyle(.bordered)

                Button("Next →") { withAnimation { step = .epgSetup } }
                    .buttonStyle(.borderedProminent)
            }
        }
        .padding(40)
    }

    private var epgSetupStep: some View {
        VStack(spacing: 20) {
            Image(systemName: "tv.fill")
                .font(.system(size: 48))
                .foregroundStyle(Color.accentColor)

            Text("Set Up TV Guide (EPG)")
                .font(.title2.bold())

            Text("Enter your EPG URL so NativeStream can show\nwhat's on and upcoming match times.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)

            TextField("https://localhost:8888/epg.xml", text: $epgURLInput)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 340)

            Text("Or use a public source like https://epghub.xyz")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
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
        .padding(40)
    }

    private var completeStep: some View {
        VStack(spacing: 20) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(.green)

            Text("You're all set!")
                .font(.title2.bold())

            Text("NativeStream is ready. Select a channel from the sidebar and enjoy hardware-decoded, buffer-free sports streaming.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)

            Button("Start Watching") {
                settings.onboardingComplete = true
                onComplete()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(40)
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
            withAnimation { step = .channelSetup }
        }
    }
}

// MARK: - Code block helper

struct CodeBlock: View {
    let code: String
    init(_ code: String) { self.code = code }

    var body: some View {
        HStack {
            Text(code)
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
            Spacer()
            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(code, forType: .string)
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.caption)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
        }
        .padding(10)
        .background(.background.secondary)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .frame(maxWidth: 360)
    }
}
