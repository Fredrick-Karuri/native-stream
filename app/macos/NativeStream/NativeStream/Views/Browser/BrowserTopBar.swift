/// TopBar.swift
///
/// Top bar for the All Channels browser.
/// Renders the title, search field, channel count, and add-channel button.
/// All state flows in via bindings — no VM dependency.

import SwiftUI
extension BrowserScreen{
    struct TopBar: View {
        
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
                    // Visual feedback: Turns bright when input is actively selected
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
                
                // UX Addition: Clear button appears dynamically when text is typed
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
                    .stroke(searchFocused ? NS.accent : NS.border2, lineWidth: searchFocused ? 1.0 : 0.5)
            )
            .contentShape(.interaction, Rectangle())
            .onTapGesture {
                searchFocused = true
            }
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
                .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.accentBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
        }
    }
}
