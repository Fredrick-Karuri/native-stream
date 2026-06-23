/// BrowserViewModel.swift
///
/// Owns all state and logic for the All Channels browser:
/// search text, group selection, section computation, and add-channel sheet.
/// Lifted above BrowserScreen so state survives destination switches in AppShell.

import Foundation
import Observation

@Observable
@MainActor
final class BrowserViewModel {

    // MARK: - State

    var searchText:    String  = ""
    var selectedGroup: String? = nil
    var showAddChannel: Bool   = false

    // Derived — recomputed via recomputeSections()
    private(set) var groupedSections: [ChannelSection] = []
    private(set) var allGroupNames:   [String]         = []

    // MARK: - Input

    /// Call whenever the upstream channel list changes.
    func recomputeSections(channels: [Channel]) {
        let filtered = filteredChannels(from: channels)
        let groups   = Dictionary(grouping: filtered, by: \.groupTitle)
        let sorted   = groups.keys.sorted().map { ChannelSection(name: $0, channels: groups[$0]!) }

        allGroupNames   = sorted.map(\.name)
        groupedSections = selectedGroup == nil ? sorted : sorted.filter { $0.name == selectedGroup }
    }

    func clearGroupWhenSearching() {
        selectedGroup = nil
    }

    // MARK: - Derived

    func filteredChannels(from channels: [Channel]) -> [Channel] {
        guard !searchText.isEmpty else { return channels }
        return channels.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.groupTitle.localizedCaseInsensitiveContains(searchText)
        }
    }

    var filteredCount: Int {
        // Exposed so the top bar can show the count without re-filtering.
        // Requires a call to recomputeSections first.
        groupedSections.reduce(0) { $0 + $1.channels.count }
    }
}

// MARK: - Section model

struct ChannelSection {
    let name: String
    let channels: [Channel]
}
