// ServerUnavailableView.swift — NS-161
// Shown on launch when the StreamServer is not reachable.

import SwiftUI

struct ServerUnavailableView: View {

    @Environment(SettingsStore.self) private var settings
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "server.rack")
                .font(.system(size: 52))
                .foregroundStyle(.secondary)

            VStack(spacing: 8) {
                Text("StreamServer Not Running")
                    .font(.title2.bold())
                Text("NativeStream needs the local Go server running at\n\(settings.serverURLString)")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            VStack(alignment: .leading, spacing: 6) {
                Label("Start the server", systemImage: "terminal")
                    .font(.headline)
                Text("Open Terminal and run:")
                    .foregroundStyle(.secondary)
                Text("make run-server")
                    .font(.system(.body, design: .monospaced))
                    .padding(8)
                    .background(.background.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                Text("Or install it as a background service that starts on login:")
                    .foregroundStyle(.secondary)
                Text("make install-service")
                    .font(.system(.body, design: .monospaced))
                    .padding(8)
                    .background(.background.secondary)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }
            .padding()
            .background(.background.secondary)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            HStack(spacing: 12) {
                Button("Open Settings") {
                    NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
                }
                .buttonStyle(.bordered)

                Button("Retry") { onRetry() }
                    .buttonStyle(.borderedProminent)
            }
        }
        .padding(40)
        .frame(maxWidth: 480)
    }
}