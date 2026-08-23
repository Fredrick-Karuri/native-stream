//
//  ServerStep.swift
//
import SwiftUI

struct ServerStep: View {
    @Binding var urlInput: String
    let connectionState: OnboardingConnectionState
    let discovery: ServerDiscoveryService
    let onConnect: (String) -> Void
    let onAdvance: () -> Void
    let onSkip: () -> Void
 
    @State private var showServer    = false
    @State private var showPlaylist  = false
    @State private var showEpg       = false
    @State private var progressTask: Task<Void, Never>?
    @State private var pairingVM: PairingViewModel?
    @State private var pairingApproved = false

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
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
        .onChange(of: connectionState) { _, state in
            if case .success(_, _, let hasEpg, _) = state {
                progressTask?.cancel()
                startPairingIfNeeded()
                progressTask = Task {
                    withAnimation { showServer = true }
                    try? await Task.sleep(for: .milliseconds(300))
                    guard !Task.isCancelled else { return }
                    withAnimation { showPlaylist = true }
                    try? await Task.sleep(for: .milliseconds(300))
                    guard !Task.isCancelled else { return }
                    if hasEpg { withAnimation { showEpg = true } }
                    try? await Task.sleep(for: .milliseconds(600))
                    guard !Task.isCancelled else { return }
                    await waitForPairingThenAdvance()
                }
            }
        }
        .onDisappear {
            pairingVM?.stop()
        }
    }
 
    private func startPairingIfNeeded() {
        guard pairingVM == nil else { return }
        let vm = PairingViewModel(onApproved: { _ in
            pairingApproved = true
        })
        pairingVM = vm
        vm.start()
    }
 
    private func waitForPairingThenAdvance() async {
        while !pairingApproved, !Task.isCancelled {
            if case .expired = pairingVM?.state { return }
            if case .denied = pairingVM?.state { return }
            if case .failed = pairingVM?.state { return }
            try? await Task.sleep(for: .milliseconds(200))
        }
        guard !Task.isCancelled, pairingApproved else { return }
        onAdvance()
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
            } else if discovery.discoveredURL != nil {
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
            if showPlaylist, let pairingVM {
                pairingStatusView(for: pairingVM.state)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
 
    @ViewBuilder
    private func pairingStatusView(for state: PairingViewModel.PairingState) -> some View {
        switch state {
        case .idle, .starting:
            HStack(spacing: NS.Spacing.sm) {
                ProgressView().controlSize(.small)
                Text("Preparing device pairing…")
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text3)
            }
 
        case .waitingForApproval(let code):
            VStack(alignment: .leading, spacing: NS.Spacing.xs) {
                Text("Approve this device from your server's admin page")
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text3)
                Text(code)
                    .font(.system(.title2, design: .monospaced, weight: .bold))
                    .foregroundStyle(NS.accent)
                    .tracking(4)
            }
 
        case .approved:
            Text("✓ Device paired")
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.accent)
 
        case .expired:
            pairingRetryView(message: "Pairing code expired")
 
        case .denied:
            pairingRetryView(message: "Pairing was denied")
 
        case .failed(let message):
            pairingRetryView(message: message)
        }
    }
 
    private func pairingRetryView(message: String) -> some View {
        HStack(spacing: NS.Spacing.sm) {
            Text("✗ \(message)")
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.red)
            Button("Retry") {
                pairingApproved = false
                pairingVM?.start()
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
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
                    "Check the IP in your server's terminal output"
                ]
                case .noPlaylist: return [
                    "Server reached but no playlist found",
                    "Check StreamServer is running: make run-server"
                ]
                case .certificateInvalid: return [
                    "The server's certificate isn't trusted",
                    "Contact your server admin, or check the cert on your hosted instance"
                ]
                case .unknown: return [
                    "Something went wrong — check the server logs"
                ]
                }            }()

            ForEach(suggestions, id: \.self) { suggestion in
                Text("→ \(suggestion)")
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
