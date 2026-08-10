// SettingsStore.swift
// Properties are now stored @Observable fields, not computed UserDefaults
// accessors. @Observable tracks mutations correctly so AppShell.onChange(of:)
// fires when values change from the Settings UI.

import Foundation
import Observation
import SwiftUI

@Observable
final class SettingsStore {

    // MARK: - Stored properties (loaded from UserDefaults in init)

    var bufferPreset: BufferPreset {
        didSet { defaults.set(bufferPreset.rawValue, forKey: Keys.bufferPreset) }
    }

    var epgURLString: String {
        didSet { defaults.set(epgURLString, forKey: Keys.epgURL) }
    }

    var epgRefreshInterval: RefreshInterval {
        didSet { defaults.set(epgRefreshInterval.rawValue, forKey: Keys.epgRefreshInterval) }
    }

    var serverURLString: String {
        didSet { defaults.set(serverURLString, forKey: Keys.serverURL) }
    }

    var onboardingComplete: Bool {
        didSet { defaults.set(onboardingComplete, forKey: Keys.onboardingComplete) }
    }

    var proxyEnabled: Bool {
        didSet { defaults.set(proxyEnabled, forKey: Keys.proxyEnabled) }
    }

    var controlDeviceID: String {
        didSet { defaults.set(controlDeviceID, forKey: Keys.controlDeviceID) }
    }

    // MARK: - Init

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let ud = defaults
        bufferPreset       = BufferPreset(rawValue: ud.string(forKey: Keys.bufferPreset) ?? "") ?? .balanced
        epgURLString       = ud.string(forKey: Keys.epgURL) ?? ""
        epgRefreshInterval = RefreshInterval(rawValue: ud.string(forKey: Keys.epgRefreshInterval) ?? "") ?? .sixHours
        serverURLString    = ud.string(forKey: Keys.serverURL) ?? "http://localhost:8888"
        onboardingComplete = ud.bool(forKey: Keys.onboardingComplete)
        proxyEnabled       = ud.bool(forKey: Keys.proxyEnabled)

        if let existingID = ud.string(forKey: Keys.controlDeviceID) {
            controlDeviceID = existingID
        } else {
            let newID = UUID().uuidString
            controlDeviceID = newID
            ud.set(newID, forKey: Keys.controlDeviceID)  // didSet won't fire during init — persist explicitly
        }
    }

    // MARK: - Computed

    var epgURL: URL? { URL(string: epgURLString) }
    var serverURL: URL? { URL(string: serverURLString) }

    // MARK: - Discovery

    func confirmDiscoveredURL(_ url: URL) {
        serverURLString = url.absoluteString
        Task { await APIClient.shared.setBaseURL(url) }
    }

    // MARK: - Keys

    private enum Keys {
        static let bufferPreset       = "bufferPreset"
        static let epgURL             = "epgURL"
        static let epgRefreshInterval = "epgRefreshInterval"
        static let serverURL          = "serverURL"
        static let onboardingComplete = "onboardingComplete"
        static let controlDeviceID    = "controlDeviceID"
        static let proxyEnabled = "proxyEnabled"
        static let allKeys: [String]  = [bufferPreset, epgURL, epgRefreshInterval, serverURL, onboardingComplete, proxyEnabled]
        // controlDeviceID intentionally excluded from allKeys — device identity
        // should survive factory reset so the server recognizes returning devices
    }

    // MARK: - Reset

    func resetAll() {
        Keys.allKeys.forEach { defaults.removeObject(forKey: $0) }
        bufferPreset       = .balanced
        epgURLString       = ""
        epgRefreshInterval = .sixHours
        serverURLString    = "http://localhost:8888"
        onboardingComplete = false
        proxyEnabled       = false
    }
}
