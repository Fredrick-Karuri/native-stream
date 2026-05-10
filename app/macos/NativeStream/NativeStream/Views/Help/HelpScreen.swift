// HelpScreen.swift
// In-app help panel. Two tabs: User Guide and Developer Reference.
// Accessible from the ? icon at the bottom of SportNavRail.

import SwiftUI

// MARK: - Help Screen

struct HelpScreen: View {

    enum HelpTab { case userGuide, developerRef }

    @State private var tab: HelpTab = .userGuide
    @State private var searchText = ""

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            HStack(spacing: 0) {
                sectionList
                Divider().overlay(NS.border)
                contentArea
            }
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        ZStack {
            HStack {
                HStack(spacing: NS.Spacing.xxs) {
                    helpTab("User Guide", tab: .userGuide)
                    helpTab("Developer",  tab: .developerRef)
                }
                .padding(NS.Spacing.xxs)
                .background(NS.surface2)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                Spacer()
            }

            HStack(spacing: NS.Spacing.xs) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: NS.Help.inlineIconSize))
                    .foregroundStyle(NS.text3)
                TextField("Search…", text: $searchText)
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text)
                    .textFieldStyle(.plain)
                    .frame(width: NS.Help.searchWidth)
            }
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: NS.Help.searchHeight)
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    private func helpTab(_ label: String, tab: HelpTab) -> some View {
        Button { self.tab = tab } label: {
            Text(label)
                .font(NS.Font.captionMed)
                .foregroundStyle(self.tab == tab ? NS.accent2 : NS.text3)
                .padding(.horizontal, NS.Spacing.md)
                .frame(height: NS.Help.tabHeight)
                .background(self.tab == tab ? NS.accentGlow : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                .overlay(
                    RoundedRectangle(cornerRadius: NS.Radius.sm)
                        .stroke(self.tab == tab ? NS.accentBorder : Color.clear, lineWidth: 0.5)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Sections sidebar

    @State private var selectedSection: String = ""

    private var sections: [HelpSection] {
        let all = tab == .userGuide ? userGuideSections : developerSections
        guard !searchText.isEmpty else { return all }
        return all.compactMap { section in
            let filteredItems = section.items.filter {
                $0.title.localizedCaseInsensitiveContains(searchText) ||
                $0.body.localizedCaseInsensitiveContains(searchText)
            }
            guard !filteredItems.isEmpty else { return nil }
            return HelpSection(title: section.title, icon: section.icon, items: filteredItems)
        }
    }

    private var sectionList: some View {
        ScrollView {
            VStack(spacing: 2) {
                ForEach(sections, id: \.title) { section in
                    Button {
                        selectedSection = section.title
                    } label: {
                        HStack(spacing: NS.Spacing.sm) {
                            Image(systemName: section.icon)
                                .font(.system(size: NS.Settings.navIconSize))
                                .foregroundStyle(selectedSection == section.title ? NS.accent2 : NS.text3)
                                .frame(width: NS.Settings.navIconSize)
                            Text(section.title)
                                .font(NS.Font.captionMed)
                                .foregroundStyle(selectedSection == section.title ? NS.accent2 : NS.text2)
                            Spacer()
                        }
                        .padding(.horizontal, NS.Spacing.sm)
                        .frame(height: NS.Settings.navItemHeight)
                        .background(selectedSection == section.title ? NS.accentGlow : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                        .overlay(
                            RoundedRectangle(cornerRadius: NS.Radius.md)
                                .stroke(selectedSection == section.title ? NS.accentBorder : Color.clear, lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(NS.Spacing.sm)
        }
        .frame(width: NS.Help.sidebarWidth)
        .background(NS.surface)
        .onAppear {
            if selectedSection.isEmpty, let first = sections.first {
                selectedSection = first.title
            }
        }
        .onChange(of: tab) {
            selectedSection = sections.first?.title ?? ""
        }
        .onChange(of: searchText) {
            if let first = sections.first { selectedSection = first.title }
        }
    }

    // MARK: - Content area

    private var contentArea: some View {
        ScrollView {
            if let section = sections.first(where: { $0.title == selectedSection }) {
                VStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                    ForEach(section.items, id: \.title) { item in
                        HelpItemView(item: item, search: searchText)
                    }
                }
                .padding(NS.Spacing.xxl)
            } else {
                VStack(spacing: NS.Spacing.md) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: NS.Help.emptyIconSize))
                        .foregroundStyle(NS.text3)
                    Text("No results for \"\(searchText)\"")
                        .font(NS.Font.display)
                        .foregroundStyle(NS.text)
                    Text("Try different keywords.")
                        .font(NS.Font.caption)
                        .foregroundStyle(NS.text3)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.top, NS.Help.emptyTopPadding)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Help Item View

struct HelpItemView: View {
    let item: HelpItem
    let search: String

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            Text(item.title)
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            ForEach(item.blocks, id: \.id) { block in
                switch block.kind {
                case .text:
                    Text(block.content)
                        .font(NS.Font.body)
                        .foregroundStyle(NS.text2)
                        .fixedSize(horizontal: false, vertical: true)
                case .code:
                    NSCodeBlock(code: block.content)
                case .tip:
                    HStack(alignment: .top, spacing: NS.Spacing.sm) {
                        Image(systemName: "lightbulb.fill")
                            .font(.system(size: NS.Help.inlineIconSize))
                            .foregroundStyle(NS.amber)
                            .padding(.top, NS.Spacing.xxs)
                        Text(block.content)
                            .font(NS.Font.caption)
                            .foregroundStyle(NS.text2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(NS.Spacing.md)
                    .background(NS.amber.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.amber.opacity(0.15), lineWidth: 0.5))
                case .warning:
                    HStack(alignment: .top, spacing: NS.Spacing.sm) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(NS.live)
                            .padding(.top, 2)
                        Text(block.content)
                            .font(NS.Font.caption)
                            .foregroundStyle(NS.text2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(NS.Spacing.md)
                    .background(NS.live.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.live.opacity(0.15), lineWidth: 0.5))
                }
            }
        }
        .padding(.bottom, NS.Spacing.md)
        .overlay(alignment: .bottom) {
            Rectangle().fill(NS.border).frame(height: 0.5)
        }
    }
}

// MARK: - Content model

struct HelpSection {
    let title: String
    let icon: String
    let items: [HelpItem]
}

struct HelpItem {
    let title: String
    let blocks: [HelpBlock]

    // Convenience for items with only a body
    var body: String { blocks.map(\.content).joined(separator: " ") }
}

struct HelpBlock: Identifiable {
    let id = UUID()
    let kind: Kind
    let content: String

    enum Kind { case text, code, tip, warning }

    static func text(_ s: String) -> HelpBlock { .init(kind: .text, content: s) }
    static func code(_ s: String) -> HelpBlock { .init(kind: .code, content: s) }
    static func tip(_ s: String)  -> HelpBlock { .init(kind: .tip,  content: s) }
    static func warn(_ s: String) -> HelpBlock { .init(kind: .warning, content: s) }
}

