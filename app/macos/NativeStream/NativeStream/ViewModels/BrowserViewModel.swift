/// BrowserViewModel.swift
///
/// Owns all state and logic for the All Channels browser.
/// MAC-BROWSE-002: selectedSource + source-aware filtering
/// MAC-BROWSE-006: favourites chip
/// MAC-BROWSE-007: sub-group chips
/// MAC-BROWSE-010: debounced off-main-actor recomputation

import Foundation
import Observation

@Observable
@MainActor
final class BrowserViewModel {

    // MARK: - State

    var searchText:         String         = ""
    var selectedGroup:      String?        = nil
    var selectedSubGroup:   String?        = nil
    var selectedSource:     PlaylistSource? = nil
    var showFavouritesOnly: Bool           = false
    var showAddChannel:     Bool           = false
    var selectedSport: SportCategory? = nil


    // Derived — recomputed via recomputeSections()
    private(set) var groupedSections: [ChannelSection] = []
    private(set) var allGroupNames:   [String]         = []
    private(set) var subGroupNames:   [String]         = []

    // MARK: - Private

    private var computeVersion = 0
    private let defaults       = UserDefaults.standard
    private let selectedSourceKey = "browser.selectedSourceId"

    // MARK: - Init

    init() {
        // Restore persisted source selection — resolved against sources later
        // via restoreSelection(from:) called by BrowserScreen on appear
    }

    // MARK: - Source selection

    func selectSource(_ source: PlaylistSource?, channels: [Channel]) {
        selectedSource   = source
        selectedGroup    = nil
        selectedSubGroup = nil
        defaults.set(source?.id.uuidString ?? "", forKey: selectedSourceKey)
        recomputeSections(channels: channels)
    }

    func restoreSelection(from sources: [PlaylistSource], channels: [Channel]) {
        guard selectedSource == nil,
              let savedId = defaults.string(forKey: selectedSourceKey),
              !savedId.isEmpty,
              let match = sources.first(where: { $0.id.uuidString == savedId })
        else { return }
        selectedSource = match
        recomputeSections(channels: channels)
    }

    // MARK: - Recompute (debounced, off main actor)

    /// Call whenever channels, search, group, subGroup, source, or favourites change.
    func recomputeSections(channels: [Channel], favouriteIDs: Set<String> = []) {
        computeVersion += 1
        let version      = computeVersion
        let search       = searchText
        let group        = selectedGroup
        let subGroup     = selectedSubGroup
        let source       = selectedSource
        let favsOnly     = showFavouritesOnly

        Task.detached(priority: .userInitiated) { [weak self] in
            // Debounce search input — skip stale tasks
            if !search.isEmpty {
                try? await Task.sleep(for: .milliseconds(150))
            }

            // Discard if a newer recompute was triggered
            guard let self, await self.computeVersion == version else { return }

            let result = Self.compute(
                channels:     channels,
                search:       search,
                group:        group,
                subGroup:     subGroup,
                source:       source,
                favsOnly:     favsOnly,
                favouriteIDs: favouriteIDs
            )

            await MainActor.run {
                guard self.computeVersion == version else { return }
                self.groupedSections = result.sections
                self.allGroupNames   = result.groupNames
                self.subGroupNames   = result.subGroupNames
            }
        }
    }

    // MARK: - Pure computation (nonisolated)

    private nonisolated static func compute(
        channels:     [Channel],
        search:       String,
        group:        String?,
        subGroup:     String?,
        source:       PlaylistSource?,
        favsOnly:     Bool,
        favouriteIDs: Set<String>
    ) -> ComputeResult {

        var filtered = channels

        // Source filter
        if let source, !source.isAll {
            filtered = filtered.filter { $0.sourceId == source.id.uuidString }
        }

        // Favourites filter
        if favsOnly {
            filtered = filtered.filter { favouriteIDs.contains($0.id) }
        }

        // Search filter
        if !search.isEmpty {
            filtered = filtered.filter {
                $0.name.localizedCaseInsensitiveContains(search) ||
                $0.groupTitle.localizedCaseInsensitiveContains(search)
            }
        }

        // Sub-group filter
        if let subGroup, !subGroup.isEmpty {
            filtered = filtered.filter { $0.subGroupTitle == subGroup }
        }

        // Group names from unfiltered source-scoped channels
        let sourceScoped = source == nil || source!.isAll
            ? channels
            : channels.filter { $0.sourceId == source!.id.uuidString }
        let groupNames = Dictionary(grouping: sourceScoped, by: \.groupTitle)
            .keys.sorted()

        // Sub-group names for active group
        let subGroupNames: [String]
        if let group, source != nil && !source!.isAll {
            subGroupNames = sourceScoped
                .filter { $0.groupTitle == group && !$0.subGroupTitle.isEmpty }
                .map(\.subGroupTitle)
                .uniqued()
                .sorted()
        } else {
            subGroupNames = []
        }

        // Section grouping
        let grouped = Dictionary(grouping: filtered, by: \.groupTitle)
        var sections = grouped.keys.sorted()
            .map { ChannelSection(name: $0, channels: grouped[$0]!) }
        if let group {
            sections = sections.filter { $0.name == group }
        }

        return ComputeResult(
            sections:      sections,
            groupNames:    groupNames,
            subGroupNames: subGroupNames
        )
    }

    // MARK: - Mutators

    func selectGroup(_ group: String?, channels: [Channel], favouriteIDs: Set<String> = []) {
        selectedGroup        = group
        selectedSubGroup     = nil
        selectedSport        = nil
        showFavouritesOnly   = false
        recomputeSections(channels: channels, favouriteIDs: favouriteIDs)
    }

    func selectSubGroup(_ subGroup: String?, channels: [Channel], favouriteIDs: Set<String> = []) {
        selectedSubGroup = subGroup
        recomputeSections(channels: channels, favouriteIDs: favouriteIDs)
    }
    
    func selectSport(_ sport: SportCategory?, channels: [Channel], favouriteIDs: Set<String> = []) {
        selectedSport = sport
        recomputeSections(channels: channels, favouriteIDs: favouriteIDs)
    }

    func toggleFavourites(channels: [Channel], favouriteIDs: Set<String>) {
        if !showFavouritesOnly {
            showFavouritesOnly = true
            selectedGroup      = nil
            selectedSubGroup   = nil
            recomputeSections(channels: channels, favouriteIDs: favouriteIDs)
        }
        // Second tap does nothing — mirrors Android onToggleFavourites behaviour
    }

    func clearGroupWhenSearching(channels: [Channel], favouriteIDs: Set<String> = []) {
        selectedGroup    = nil
        selectedSubGroup = nil
        recomputeSections(channels: channels, favouriteIDs: favouriteIDs)
    }

    // MARK: - Derived

    var filteredCount: Int {
        groupedSections.reduce(0) { $0 + $1.channels.count }
    }

    // MARK: - Private types

    private struct ComputeResult {
        let sections:      [ChannelSection]
        let groupNames:    [String]
        let subGroupNames: [String]
    }
}

// MARK: - Section model

struct ChannelSection: Identifiable {
    var id: String { name }
    let name:     String
    let channels: [Channel]
}

// MARK: - Array uniqued helper

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
