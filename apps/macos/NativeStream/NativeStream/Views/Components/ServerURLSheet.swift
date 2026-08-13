
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
    @Binding var tokenInput: String

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.lg) {
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                Text("Server URL").font(NS.Font.caption).foregroundStyle(NS.text3)
                NSTextField(placeholder: ServerURLResolver.hostedDefaultURL, text: $urlInput)
            }
            VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                Text("API Token").font(NS.Font.caption).foregroundStyle(NS.text3)
                NSTextField(placeholder: "Required for hosted servers", text: $tokenInput)
                Text("Leave blank for a self-hosted server on your local network.")
                    .font(NS.Font.monoSm).foregroundStyle(NS.text3)
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
            // Distinct from Cancel: Cancel discards edits and keeps whatever
            // was in effect before opening the sheet. Clear Override stops
            // overriding entirely, falling back to mDNS/hosted-default
            // resolution — the only way back to "auto" once someone has
            // set a manual value, so it needs to exist and be reachable,
            // not just Cancel.
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
    @State private var tokenInput: String

    init(settings: SettingsStore) {
        // Pre-filled with the EFFECTIVE URL (resolvedServerURL), not the raw
        // override — this is the fix for UX item #4. If nothing's overridden,
        // this shows the hosted default already in effect, so opening the
        // sheet never reveals a value that contradicts what the health card
        // just showed.
        _urlInput = State(initialValue: settings.resolvedServerURL.url)
        _tokenInput = State(initialValue: settings.apiToken ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            Text("Server").font(NS.Font.heading).foregroundStyle(NS.text)

            ServerURLFieldPair(urlInput: $urlInput, tokenInput: $tokenInput)

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
                    // Saving the resolved value back as itself (untouched
                    // field) is a harmless no-op in outcome — it just
                    // re-sets the same override it already displayed.
                    settings.serverURLString = urlInput.isEmpty ? nil : urlInput
                    if !tokenInput.isEmpty {
                        settings.setAPIToken(tokenInput)
                    }
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
