//
//  Helpers.swift
//  NativeStream

import SwiftUI

// MARK: - Shared helpers

struct SectionTitle: View {
    let title: String
    init(_ title: String) { self.title = title }
    var body: some View {
        Text(title.uppercased()).font(NS.Font.label).kerning(1.0).foregroundStyle(NS.text3)
    }
}

struct SettingsRow<Control: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var control: Control

    var body: some View {
        HStack(alignment: .center, spacing: NS.Spacing.lg) {
            VStack(alignment: .leading, spacing: NS.Spacing.xxs) {
                Text(title).font(NS.Font.captionMed).foregroundStyle(NS.text)
                if !subtitle.isEmpty {
                    Text(subtitle).font(NS.Font.monoSm).foregroundStyle(NS.text3)
                }
            }
            Spacer()
            control
        }
        .padding(NS.Spacing.md)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.lg).stroke(NS.border, lineWidth: 0.5))
    }
}

struct AddButton: View {
    let label: String
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        
        Button(action: action) {
            HStack {
                Text(label)
                    .font(NS.Font.caption)
                    .foregroundStyle(isHovered ? NS.accent2 : NS.text3)
                Spacer()
            }
            .padding(.horizontal, NS.Spacing.md)
            .frame(maxWidth: .infinity)
            .frame(height: NS.Helpers.addButtonHeight)
            .contentShape(Rectangle())
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.lg)
                    .stroke(isHovered ? NS.accent : NS.border2, style: StrokeStyle(dash: [4]))
            )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
        .animation(.easeOut(duration: 0.1), value: isHovered)
    }
}

struct NSTextField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        TextField(placeholder, text: $text)
            .font(NS.Font.body)
            .foregroundStyle(NS.text)
            .textFieldStyle(.plain)
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: NS.Settings.navItemHeight)
            .background(NS.bg)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))
    }
}


