// BrowserViewModelComputeTests
// Unit tests for BrowserViewModel's pure, nonisolated compute() static function:
// source/favourites/search/subgroup filtering, group-name derivation, subgroup-name
// derivation, and section grouping.

// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class BrowserViewModelComputeTests: XCTestCase {

    // MARK: Helpers

    private func channel(
        tvgId: String,
        name: String,
        groupTitle: String = "Uncategorised",
        subGroupTitle: String = "",
        sourceId: String = ""
    ) -> Channel {
        Channel(
            tvgId: tvgId,
            name: name,
            groupTitle: groupTitle,
            subGroupTitle: subGroupTitle,
            sourceId: sourceId,
            streamURL: URL(string: "http://example.com/\(tvgId).m3u8")!
        )
    }

    private func source(id: UUID = UUID(), label: String = "Source") -> PlaylistSource {
        PlaylistSource(id: id, label: label, url: URL(string: "http://example.com/playlist.m3u")!)
    }

    // MARK: No filters

    func testNoFiltersGroupsAllChannelsBySortedGroupTitle() {
        let channels = [
            channel(tvgId: "1", name: "BBC One", groupTitle: "Entertainment"),
            channel(tvgId: "2", name: "Sky Sports", groupTitle: "Sports"),
            channel(tvgId: "3", name: "BBC Two", groupTitle: "Entertainment")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.groupNames, ["Entertainment", "Sports"])
        XCTAssertEqual(result.sections.map(\.name).sorted(), ["Entertainment", "Sports"])
        XCTAssertEqual(result.sections.reduce(0) { $0 + $1.channels.count }, 3)
    }

    // MARK: Group filter

    func testGroupFilterKeepsOnlyMatchingSection() {
        let channels = [
            channel(tvgId: "1", name: "BBC One", groupTitle: "Entertainment"),
            channel(tvgId: "2", name: "Sky Sports", groupTitle: "Sports")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: "Sports", subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.sections.map(\.name), ["Sports"])
        XCTAssertEqual(result.sections.first?.channels.map(\.name), ["Sky Sports"])
    }

    func testGroupFilterForNonExistentGroupProducesEmptySections() {
        let channels = [channel(tvgId: "1", name: "BBC One", groupTitle: "Entertainment")]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: "Nonexistent", subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        XCTAssertTrue(result.sections.isEmpty)
    }

    // MARK: Search filter

    func testSearchFiltersByChannelNameCaseInsensitive() {
        let channels = [
            channel(tvgId: "1", name: "BBC One", groupTitle: "Entertainment"),
            channel(tvgId: "2", name: "Sky Sports", groupTitle: "Sports")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "bbc", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.map(\.name), ["BBC One"])
    }

    func testSearchFiltersByGroupTitleCaseInsensitive() {
        let channels = [
            channel(tvgId: "1", name: "Channel A", groupTitle: "Sports Extra"),
            channel(tvgId: "2", name: "Channel B", groupTitle: "News")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "sports", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.map(\.name), ["Channel A"])
    }

    func testSearchMatchingNothingProducesEmptySections() {
        let channels = [channel(tvgId: "1", name: "BBC One", groupTitle: "Entertainment")]

        let result = BrowserViewModel.compute(
            channels: channels, search: "zzz-no-match", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        XCTAssertTrue(result.sections.isEmpty)
    }

    // MARK: Favourites filter

    func testFavsOnlyFiltersToFavouriteIDs() {
        let channels = [
            channel(tvgId: "fav1", name: "Favourite Channel"),
            channel(tvgId: "other1", name: "Other Channel")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: nil, favsOnly: true, favouriteIDs: ["fav1"]
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.map(\.name), ["Favourite Channel"])
    }

    func testFavsOnlyWithEmptyFavouriteIDsProducesEmptySections() {
        let channels = [channel(tvgId: "1", name: "A")]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: nil, favsOnly: true, favouriteIDs: []
        )

        XCTAssertTrue(result.sections.isEmpty)
    }

    // MARK: Source filter

    func testSourceFilterScopesToMatchingSourceId() {
        let sourceA = source(label: "Source A")
        let sourceB = source(label: "Source B")
        let channels = [
            channel(tvgId: "1", name: "A Channel", sourceId: sourceA.id.uuidString),
            channel(tvgId: "2", name: "B Channel", sourceId: sourceB.id.uuidString)
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: sourceA, favsOnly: false, favouriteIDs: []
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.map(\.name), ["A Channel"])
    }

    func testAllSourcesSentinelDoesNotFilterByStreamSource() {
        let channels = [
            channel(tvgId: "1", name: "A Channel", sourceId: "source-a"),
            channel(tvgId: "2", name: "B Channel", sourceId: "source-b")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: .allSources, favsOnly: false, favouriteIDs: []
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.count, 2)
    }

    func testNilSourceDoesNotFilterByStreamSource() {
        let channels = [
            channel(tvgId: "1", name: "A Channel", sourceId: "source-a"),
            channel(tvgId: "2", name: "B Channel", sourceId: "source-b")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.count, 2)
    }

    // MARK: Sub-group filter and derivation

    func testSubGroupNamesDerivedForActiveGroupWhenSourceIsSpecific() {
        let specificSource = source(label: "My Source")
        let channels = [
            channel(tvgId: "1", name: "A", groupTitle: "Sports", subGroupTitle: "Football", sourceId: specificSource.id.uuidString),
            channel(tvgId: "2", name: "B", groupTitle: "Sports", subGroupTitle: "Rugby", sourceId: specificSource.id.uuidString),
            channel(tvgId: "3", name: "C", groupTitle: "Sports", subGroupTitle: "", sourceId: specificSource.id.uuidString)
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: "Sports", subGroup: nil,
            source: specificSource, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.subGroupNames, ["Football", "Rugby"])
    }

    func testSubGroupNamesEmptyWhenSourceIsAllSourcesSentinel() {
        let channels = [
            channel(tvgId: "1", name: "A", groupTitle: "Sports", subGroupTitle: "Football", sourceId: "any")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: "Sports", subGroup: nil,
            source: .allSources, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.subGroupNames, [])
    }

    func testSubGroupNamesEmptyWhenSourceIsNil() {
        let channels = [
            channel(tvgId: "1", name: "A", groupTitle: "Sports", subGroupTitle: "Football")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: "Sports", subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.subGroupNames, [])
    }

    func testSubGroupNamesExcludeEmptySubGroupTitlesAndDeduplicate() {
        let specificSource = source(label: "My Source")
        let channels = [
            channel(tvgId: "1", name: "A", groupTitle: "Sports", subGroupTitle: "Football", sourceId: specificSource.id.uuidString),
            channel(tvgId: "2", name: "B", groupTitle: "Sports", subGroupTitle: "Football", sourceId: specificSource.id.uuidString),
            channel(tvgId: "3", name: "C", groupTitle: "Sports", subGroupTitle: "", sourceId: specificSource.id.uuidString)
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: "Sports", subGroup: nil,
            source: specificSource, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.subGroupNames, ["Football"])
    }

    func testSelectingSubGroupFiltersChannelsWithinGroup() {
        let channels = [
            channel(tvgId: "1", name: "A", groupTitle: "Sports", subGroupTitle: "Football"),
            channel(tvgId: "2", name: "B", groupTitle: "Sports", subGroupTitle: "Rugby")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: "Football",
            source: nil, favsOnly: false, favouriteIDs: []
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.map(\.name), ["A"])
    }

    // MARK: Group names reflect source scope, not active filters

    func testGroupNamesReflectSourceScopedChannelsIgnoringSearchAndSubGroup() {
        let channels = [
            channel(tvgId: "1", name: "BBC One", groupTitle: "Entertainment"),
            channel(tvgId: "2", name: "Sky Sports", groupTitle: "Sports")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "bbc", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        // groupNames should still list both groups even though search narrows sections
        XCTAssertEqual(result.groupNames, ["Entertainment", "Sports"])
        XCTAssertEqual(result.sections.flatMap(\.channels).count, 1)
    }

    func testGroupNamesScopedToSelectedSourceOnly() {
        let sourceA = source(label: "Source A")
        let sourceB = source(label: "Source B")
        let channels = [
            channel(tvgId: "1", name: "A", groupTitle: "Entertainment", sourceId: sourceA.id.uuidString),
            channel(tvgId: "2", name: "B", groupTitle: "Sports", sourceId: sourceB.id.uuidString)
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "", group: nil, subGroup: nil,
            source: sourceA, favsOnly: false, favouriteIDs: []
        )

        XCTAssertEqual(result.groupNames, ["Entertainment"])
    }

    // MARK: Combined filters

    func testSearchAndFavouritesFiltersCombine() {
        let channels = [
            channel(tvgId: "fav-bbc", name: "BBC Favourite"),
            channel(tvgId: "fav-itv", name: "ITV Favourite"),
            channel(tvgId: "other-bbc", name: "BBC Other")
        ]

        let result = BrowserViewModel.compute(
            channels: channels, search: "bbc", group: nil, subGroup: nil,
            source: nil, favsOnly: true, favouriteIDs: ["fav-bbc", "fav-itv"]
        )

        let allChannels = result.sections.flatMap(\.channels)
        XCTAssertEqual(allChannels.map(\.name), ["BBC Favourite"])
    }

    // MARK: Empty input

    func testEmptyChannelListProducesEmptyResult() {
        let result = BrowserViewModel.compute(
            channels: [], search: "", group: nil, subGroup: nil,
            source: nil, favsOnly: false, favouriteIDs: []
        )

        XCTAssertTrue(result.sections.isEmpty)
        XCTAssertTrue(result.groupNames.isEmpty)
        XCTAssertTrue(result.subGroupNames.isEmpty)
    }
}
