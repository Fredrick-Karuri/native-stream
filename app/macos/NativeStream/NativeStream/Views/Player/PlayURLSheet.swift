// PlayURLSheet.swift
// Direct URL playback. Paste any HLS/IPTV URL and optional headers, play immediately.
// Triggered by ⌘U from AppShell. Creates a temporary channel, not persisted.

import SwiftUI

struct PlayURLSheet: View {

    @Environment(PlayerViewModel.self) private var playerVM
    @Binding var isPresented: Bool
    var onPlay: () -> Void  // called after playerVM.playURL — AppShell shows player

    @State private var urlText     = ""
    @State private var referer     = ""
    @State private var userAgent   = ""
    @State private var showHeaders = false

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {

            Text("Play URL")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            // URL field
            VStack(alignment: .leading, spacing: NS.Spacing.xs) {
                Text("Stream URL")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
                TextField("https://example.com/stream.m3u8", text: $urlText)
                    .textFieldStyle(.roundedBorder)
                    .font(NS.Font.body)
            }

            // Headers toggle
            Button {
                withAnimation(.easeOut(duration: 0.15)) { showHeaders.toggle() }
            } label: {
                HStack(spacing: NS.Spacing.xs) {
                    Image(systemName: showHeaders ? "chevron.down" : "chevron.right")
                        .font(.system(size: 11))
                    Text("HTTP Headers (optional)")
                        .font(NS.Font.caption)
                }
                .foregroundStyle(NS.text3)
            }
            .buttonStyle(.plain)

            if showHeaders {
                VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                    headerField(label: "Referer",    placeholder: "https://example.com", text: $referer)
                    headerField(label: "User-Agent", placeholder: "Mozilla/5.0 ...",     text: $userAgent)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            Divider().overlay(NS.border)

            HStack {
                Button("Cancel") { isPresented = false }
                    .buttonStyle(.bordered)
                Spacer()
                Button("Play") { play() }
                    .buttonStyle(.borderedProminent)
                    .disabled(urlText.trimmingCharacters(in: .whitespaces).isEmpty)
                    .keyboardShortcut(.return)
            }
        }
        .padding(NS.Spacing.xxl)
        .frame(width: 440)
        .background(NS.bg)
    }

    private func headerField(label: String, placeholder: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xs) {
            Text(label).font(NS.Font.caption).foregroundStyle(NS.text3)
            TextField(placeholder, text: text)
                .textFieldStyle(.roundedBorder)
                .font(NS.Font.mono)
        }
    }

    private func play() {
        var headers: [String: String] = [:]
        if !referer.isEmpty    { headers["Referer"]    = referer }
        if !userAgent.isEmpty  { headers["User-Agent"] = userAgent }
        playerVM.playURL(urlText, headers: headers)
        isPresented = false
        onPlay()
    }
}
