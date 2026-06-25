/// TopBar.swift
///
/// Top bar for the All Channels browser.
/// MAC-BROWSE-005: NSSourcePill + NSSourcePickerView popover.

import SwiftUI

extension BrowserScreen {
    struct TopBar: View {

        @Binding var searchText:    String
        @FocusState.Binding var searchFocused: Bool

        let channelCount:   Int
        let sources:        [PlaylistSource]
        let selectedSource: PlaylistSource?
        let onSelectSource: (PlaylistSource?) -> Void
        let onAddPlaylist:  () -> Void
        let onAddChannel:   () -> Void
        let playlistVM:     PlaylistViewModel
        
        @State private var showSourcePicker = false
        @State private var showAddSource = false
        

        var body: some View {
            HStack(spacing: NS.Spacing.md) {
                Text("Browse")
                    .font(NS.Font.heading)
                    .foregroundStyle(NS.text)

                // Source pill — anchors the picker popover
                NSSourcePill(source: selectedSource) {
                    showSourcePicker.toggle()
                }
                .popover(isPresented: $showSourcePicker, arrowEdge: .bottom) {
                    NSSourcePickerView(
                        sources:        sources,
                        selectedSource: selectedSource,
                        onSelect:       { onSelectSource($0) },
                        onAddPlaylist: { showAddSource = true },
                        onDismiss:      { showSourcePicker = false }
                    )
                    .padding(NS.Spacing.xs)
                }

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
            .sheet(isPresented: $showAddSource) {
                AddSourceSheet { source in
                    showAddSource = false
                    if let source {
                        playlistVM.addSource(source)
                        Task { await playlistVM.loadAll() }
                    }
                }
            }
        }

        // MARK: - Components

        private var searchField: some View {
            HStack(spacing: NS.Spacing.xs) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: NS.Help.inlineIconSize))
                    .foregroundStyle(searchFocused ? NS.accent2 : NS.text3)

                TextField("Search channels…", text: $searchText)
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text)
                    .textFieldStyle(.plain)
                    .frame(width: NS.Browser.searchWidth)
                    .focused($searchFocused)
                    .onExitCommand {
                        if !searchText.isEmpty {
                            searchText = ""
                        } else {
                            searchFocused = false
                        }
                    }

                if !searchText.isEmpty {
                    Button(action: { searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(NS.text3)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: NS.Help.searchHeight)
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.md)
                    .stroke(searchFocused ? NS.accent : NS.border2,
                            lineWidth: searchFocused ? 1.0 : 0.5)
            )
            .contentShape(.interaction, Rectangle())
            .onTapGesture { searchFocused = true }
            .onReceive(NotificationCenter.default.publisher(for: .focusSearchField)) { _ in
                searchFocused = true
            }
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
                .overlay(RoundedRectangle(cornerRadius: NS.Radius.md)
                    .stroke(NS.accentBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
        }
    }
}
