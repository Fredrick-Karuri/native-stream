/// TopBar.swift
///
/// Top bar for the All Channels browser.
/// Renders the title, search field, channel count, and add-channel button.
/// All state flows in via bindings — no VM dependency.

import SwiftUI

struct BrowserTopBar: View {

    @Binding var searchText: String
    @FocusState.Binding var searchFocused: Bool

    let channelCount:   Int
    let onAddChannel:   () -> Void

    var body: some View {
        HStack {
            Text("All Channels")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            Spacer()

            searchField

            Spacer()

            Text("\(channelCount) channels")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)

            addChannelButton
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    // MARK: - Components

    private var searchField: some View {
        HStack(spacing: NS.Spacing.xs) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: NS.Help.inlineIconSize))
                .foregroundStyle(NS.text3)
            TextField("Search channels…", text: $searchText)
                .font(NS.Font.caption)
                .foregroundStyle(NS.text)
                .textFieldStyle(.plain)
                .frame(width: NS.Browser.searchWidth)
                .focused($searchFocused)
        }
        .padding(.horizontal, NS.Spacing.md)
        .frame(height: NS.Help.searchHeight)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))
    }

    private var addChannelButton: some View {
        Button(action: onAddChannel) {
            HStack(spacing: NS.Spacing.xs) {
                Image(systemName: "plus")
                    .font(.system(size: NS.BrowserTopBar.addIconSize, weight: .semibold))
                Text("Add Channel")
                    .font(NS.Font.captionMed)
            }
            .foregroundStyle(NS.accent)
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: NS.Help.searchHeight)
            .background(NS.accentGlow)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.accentBorder, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
    }
}
