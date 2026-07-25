//
//  EPGStep.swift
//  NativeStream
//

import SwiftUI

private let iptvOrgEPG = "https://iptv-org.github.io/epg/guides/en/xmltv.xml"

struct EPGStep: View {
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
