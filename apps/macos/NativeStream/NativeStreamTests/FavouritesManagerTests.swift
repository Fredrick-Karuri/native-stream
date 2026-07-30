// FavouritesManagerTests
// Unit tests for FavouritesManager: toggling, membership checks, filtering,
// and persistence across instances. Uses an isolated UserDefaults suite per
// test so runs never touch or leak into the real .standard defaults.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class FavouritesManagerTests: XCTestCase {

    private var defaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        suiteName = "FavouritesManagerTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        suiteName = nil
        super.tearDown()
    }

    private func channel(id: String) -> Channel {
        Channel(tvgId: id, name: id, streamURL: URL(string: "http://example.com/\(id).m3u8")!)
    }

    // MARK: Initial state

    func testStartsEmptyWhenNoStoredFavourites() {
        let manager = FavouritesManager(defaults: defaults)

        XCTAssertTrue(manager.favouriteIDs.isEmpty)
    }

    func testLoadsPreviouslyStoredFavouritesOnInit() {
        defaults.set(["bbc1", "itv1"], forKey: "favouriteChannelIDs")

        let manager = FavouritesManager(defaults: defaults)

        XCTAssertEqual(manager.favouriteIDs, ["bbc1", "itv1"])
    }

    // MARK: toggle

    func testToggleAddsChannelNotYetFavourited() {
        let manager = FavouritesManager(defaults: defaults)

        manager.toggle(channel(id: "bbc1"))

        XCTAssertTrue(manager.favouriteIDs.contains("bbc1"))
    }

    func testToggleRemovesChannelAlreadyFavourited() {
        let manager = FavouritesManager(defaults: defaults)
        manager.toggle(channel(id: "bbc1"))

        manager.toggle(channel(id: "bbc1"))

        XCTAssertFalse(manager.favouriteIDs.contains("bbc1"))
    }

    func testToggleOnlyAffectsTheGivenChannel() {
        let manager = FavouritesManager(defaults: defaults)
        manager.toggle(channel(id: "bbc1"))

        manager.toggle(channel(id: "itv1"))

        XCTAssertEqual(manager.favouriteIDs, ["bbc1", "itv1"])
    }

    // MARK: isFavourite

    func testIsFavouriteReturnsTrueForFavouritedChannel() {
        let manager = FavouritesManager(defaults: defaults)
        manager.toggle(channel(id: "bbc1"))

        XCTAssertTrue(manager.isFavourite(channel(id: "bbc1")))
    }

    func testIsFavouriteReturnsFalseForNonFavouritedChannel() {
        let manager = FavouritesManager(defaults: defaults)

        XCTAssertFalse(manager.isFavourite(channel(id: "bbc1")))
    }

    // MARK: favourites(from:)

    func testFavouritesFromReturnsOnlyFavouritedChannelsPreservingInputOrder() {
        let manager = FavouritesManager(defaults: defaults)
        manager.toggle(channel(id: "itv1"))

        let all = [channel(id: "bbc1"), channel(id: "itv1"), channel(id: "channel4")]
        let result = manager.favourites(from: all)

        XCTAssertEqual(result.map(\.id), ["itv1"])
    }

    func testFavouritesFromReturnsEmptyWhenNoneAreFavourited() {
        let manager = FavouritesManager(defaults: defaults)

        let result = manager.favourites(from: [channel(id: "bbc1"), channel(id: "itv1")])

        XCTAssertTrue(result.isEmpty)
    }

    // MARK: Persistence across instances

    func testTogglePersistsAcrossNewManagerInstanceUsingSameDefaults() {
        let firstManager = FavouritesManager(defaults: defaults)
        firstManager.toggle(channel(id: "bbc1"))

        let secondManager = FavouritesManager(defaults: defaults)

        XCTAssertTrue(secondManager.favouriteIDs.contains("bbc1"))
    }

    func testRemovalPersistsAcrossNewManagerInstanceUsingSameDefaults() {
        let firstManager = FavouritesManager(defaults: defaults)
        firstManager.toggle(channel(id: "bbc1"))
        firstManager.toggle(channel(id: "bbc1")) // remove

        let secondManager = FavouritesManager(defaults: defaults)

        XCTAssertFalse(secondManager.favouriteIDs.contains("bbc1"))
    }
}
