// SettingsStoreTests
// Unit tests for SettingsStore: default loading, persistence on mutation,
// computed URL properties, resetAll, and the controlDeviceID survives-reset
// guarantee. Uses an isolated UserDefaults suite per test so runs never touch
// or leak into the real .standard defaults.
// Run with: swift test (from package root, or via Xcode Test Navigator)

import XCTest
@testable import NativeStream

final class SettingsStoreTests: XCTestCase {

    private var defaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        suiteName = "SettingsStoreTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        suiteName = nil
        super.tearDown()
    }

    // MARK: Defaults on first launch (nothing stored)

    func testDefaultsWhenNothingStored() {
        let store = SettingsStore(defaults: defaults)

        XCTAssertEqual(store.bufferPreset, .balanced)
        XCTAssertEqual(store.epgURLString, "")
        XCTAssertEqual(store.epgRefreshInterval, .sixHours)
        XCTAssertEqual(store.serverURLString, "http://localhost:8888")
        XCTAssertFalse(store.onboardingComplete)
        XCTAssertFalse(store.proxyEnabled)
    }

    func testControlDeviceIDIsGeneratedWhenNotStored() {
        let store = SettingsStore(defaults: defaults)

        XCTAssertFalse(store.controlDeviceID.isEmpty)
        XCTAssertNotNil(UUID(uuidString: store.controlDeviceID))
    }

    func testControlDeviceIDIsStableAcrossInstancesOnceGenerated() {
        let first = SettingsStore(defaults: defaults)
        let generatedID = first.controlDeviceID

        let second = SettingsStore(defaults: defaults)

        XCTAssertEqual(second.controlDeviceID, generatedID)
    }

    // MARK: Loading previously stored values

    func testLoadsPreviouslyStoredValues() {
        defaults.set(BufferPreset.reliable.rawValue, forKey: "bufferPreset")
        defaults.set("http://epg.example.com/guide.xml", forKey: "epgURL")
        defaults.set(true, forKey: "onboardingComplete")
        defaults.set(true, forKey: "proxyEnabled")

        let store = SettingsStore(defaults: defaults)

        XCTAssertEqual(store.bufferPreset, .reliable)
        XCTAssertEqual(store.epgURLString, "http://epg.example.com/guide.xml")
        XCTAssertTrue(store.onboardingComplete)
        XCTAssertTrue(store.proxyEnabled)
    }

    func testFallsBackToDefaultWhenStoredRawValueIsInvalid() {
        defaults.set("not-a-real-preset", forKey: "bufferPreset")

        let store = SettingsStore(defaults: defaults)

        XCTAssertEqual(store.bufferPreset, .balanced)
    }

    // MARK: Persistence on mutation

    func testMutatingBufferPresetPersistsToDefaults() {
        let store = SettingsStore(defaults: defaults)

        store.bufferPreset = .reliable

        XCTAssertEqual(defaults.string(forKey: "bufferPreset"), BufferPreset.reliable.rawValue)
    }

    func testMutatingServerURLStringPersistsToDefaults() {
        let store = SettingsStore(defaults: defaults)

        store.serverURLString = "http://192.168.1.50:8888"

        XCTAssertEqual(defaults.string(forKey: "serverURL"), "http://192.168.1.50:8888")
    }

    func testMutationPersistsAcrossNewInstanceUsingSameDefaults() {
        let first = SettingsStore(defaults: defaults)
        first.onboardingComplete = true

        let second = SettingsStore(defaults: defaults)

        XCTAssertTrue(second.onboardingComplete)
    }

    // MARK: Computed URL properties

    func testEpgURLReturnsNilForEmptyString() {
        let store = SettingsStore(defaults: defaults)

        XCTAssertNil(store.epgURL)
    }

    func testEpgURLParsesValidURLString() {
        let store = SettingsStore(defaults: defaults)
        store.epgURLString = "http://epg.example.com/guide.xml"

        XCTAssertEqual(store.epgURL?.absoluteString, "http://epg.example.com/guide.xml")
    }

    func testServerURLParsesDefaultLocalhostString() {
        let store = SettingsStore(defaults: defaults)

        XCTAssertEqual(store.serverURL?.host, "localhost")
        XCTAssertEqual(store.serverURL?.port, 8888)
    }

    // MARK: resetAll

    func testResetAllRestoresDefaultsForResettableProperties() {
        let store = SettingsStore(defaults: defaults)
        store.bufferPreset       = .reliable
        store.epgURLString       = "http://epg.example.com/guide.xml"
        store.epgRefreshInterval = .oneHour
        store.serverURLString    = "http://192.168.1.50:8888"
        store.onboardingComplete = true
        store.proxyEnabled       = true

        store.resetAll()

        XCTAssertEqual(store.bufferPreset, .balanced)
        XCTAssertEqual(store.epgURLString, "")
        XCTAssertEqual(store.epgRefreshInterval, .sixHours)
        XCTAssertEqual(store.serverURLString, "http://localhost:8888")
        XCTAssertFalse(store.onboardingComplete)
        XCTAssertFalse(store.proxyEnabled)
    }

    func testResetAllClearsPersistedValuesFromDefaults() {
        let store = SettingsStore(defaults: defaults)
        store.bufferPreset = .reliable

        store.resetAll()

        XCTAssertEqual(defaults.string(forKey: "bufferPreset"), BufferPreset.balanced.rawValue)
    }

    func testResetAllDoesNotChangeControlDeviceID() {
        let store = SettingsStore(defaults: defaults)
        let originalDeviceID = store.controlDeviceID

        store.resetAll()

        XCTAssertEqual(store.controlDeviceID, originalDeviceID)
    }

    func testResetAllDoesNotRemoveControlDeviceIDFromDefaults() {
        let store = SettingsStore(defaults: defaults)
        let originalDeviceID = store.controlDeviceID

        store.resetAll()
        let freshStore = SettingsStore(defaults: defaults)

        XCTAssertEqual(freshStore.controlDeviceID, originalDeviceID)
    }
}
