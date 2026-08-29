// ServerURLSheet.swift
//
// Composed of three small pieces rather than one large body:
//   - ServerURLFieldPair   — the URL + token fields as one conceptual unit
//   - ServerURLSheetActions — Cancel / Clear Override / Save
//   - ServerURLSheet        — container, local edit state, wiring to SettingsStore

import SwiftUI

// MARK: - Field pair

struct ServerURLFieldPair: View {
    @Binding var urlInput: String
 
    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.lg) {
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                Text("Server URL").font(NS.Font.caption).foregroundStyle(NS.text3)
                NSTextField(placeholder: ServerURLResolver.hostedDefaultURL, text: $urlInput)
            }
        }
    }
}

// MARK: - Actions row

struct ServerURLSheetActions: View {
    let hasExistingOverride: Bool
    let onCancel: () -> Void
    let onClearOverride: () -> Void
    let onSave: () -> Void

    var body: some View {
        HStack(spacing: NS.Spacing.sm) {
            if hasExistingOverride {
                Button("Clear Override", role: .destructive, action: onClearOverride)
                    .buttonStyle(.bordered)
            }
            Spacer()
            Button("Cancel", action: onCancel).buttonStyle(.bordered)
            Button("Save", action: onSave).buttonStyle(.borderedProminent)
        }
    }
}

// MARK: - Sheet


struct ServerURLSheet: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(\.dismiss) private var dismiss
 
    @State private var urlInput: String
 
    init(settings: SettingsStore) {
        _urlInput = State(initialValue: settings.resolvedServerURL.url)
    }
 
    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            Text("Server").font(NS.Font.heading).foregroundStyle(NS.text)
 
            ServerURLFieldPair(urlInput: $urlInput)
 
            Text(clearOverrideExplanation)
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)
 
            ServerURLSheetActions(
                hasExistingOverride: settings.serverURLString != nil,
                onCancel: { dismiss() },
                onClearOverride: {
                    settings.serverURLString = nil
                    dismiss()
                },
                onSave: {
                    settings.serverURLString = urlInput.isEmpty ? nil : urlInput
                    dismiss()
                }
            )
        }
        .padding(NS.Spacing.xxl)
        .frame(width: 420)
    }
 
    private var clearOverrideExplanation: String {
        "Blank or cleared uses auto-resolution: your LAN server if discovered, otherwise the hosted default."
    }
}
