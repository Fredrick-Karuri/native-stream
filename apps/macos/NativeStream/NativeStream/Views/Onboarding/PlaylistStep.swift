//
//  PlaylistStep.swift

import SwiftUI


struct PlaylistStep: View {
    let connectionState: OnboardingConnectionState
    let onSourceAdded: (URL, String) -> Void
    let onAdvance: (URL?) -> Void
    let onSkip: () -> Void

    @State private var playlistInput = ""
    @State private var labelInput    = ""
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
                .onChange(of: playlistInput) { _, newURL in
                    foundEpg   = nil
                    isAdded    = false
                    // suggest a name from the last path component if user hasn't typed one
                    if labelInput.isEmpty, let url = URL(string: newURL) {
                        let stem = url.deletingPathExtension().lastPathComponent
                        if !stem.isEmpty && stem != "/" { labelInput = stem }
                    }
                }

            NSTextField(placeholder: "Name (e.g. Xumo)", text: $labelInput)
                .frame(maxWidth: 340)

            if isAdded {
                VStack(spacing: NS.Spacing.sm) {
                    Text("✓ Playlist added")
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.accent)
                    if foundEpg != nil {
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
                            onSourceAdded(url, labelInput)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(playlistInput.isEmpty || isProbing)
                }
            }
        }
        .padding(NS.Spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .frame(maxWidth: 560)
        .background(NS.bg)
        .animation(.easeInOut(duration: 0.3), value: isAdded)
    }
}
