//
//  AddSourceSheet.swift
//  NativeStream
//
//  Created by Fredrick Karuri on 25/06/2026.
//
import SwiftUI

struct AddSourceSheet: View {
    let onComplete: (PlaylistSource?) -> Void

    @State private var label:    String = ""
    @State private var url:      String = ""
    @State private var interval: RefreshInterval = .sixHours

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            Text("Add Playlist Source")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            AddSourceForm(
                label:    $label,
                url:      $url,
                interval: $interval,
                onCancel: { onComplete(nil) },
                onAdd: {
                    guard let parsed = URL(string: url) else { return }
                    onComplete(PlaylistSource(
                        label:           label.isEmpty ? (parsed.host ?? "Source") : label,
                        url:             parsed,
                        refreshInterval: interval
                    ))
                }
            )
        }
        .padding(NS.Spacing.xxl)
        .frame(width: 400)
        .background(NS.surface)
    }
}
