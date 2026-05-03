// SettingsStore
// Persists user preferences via UserDefaults / @AppStorage.

import Foundation
import Observation
import SwiftUI

@Observable
final class SettingsStore {

    // MARK: - Stored properties (backed by UserDefaults)

    var bufferPreset: BufferPreset {
        get { BufferPreset(rawValue: UserDefaults.standard.string(forKey: Keys.bufferPreset) ?? "") ?? .balanced }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: Keys.bufferPreset) }
    }

    var epgURLString: String {
        get { UserDefaults.standard.string(forKey: Keys.epgURL) ?? "" }
        set { UserDefaults.standard.set(newValue, forKey: Keys.epgURL) }
    }

    var epgURL: URL? {
        URL(string: epgURLString)
    }

    var epgRefreshInterval: RefreshInterval {
        get { RefreshInterval(rawValue: UserDefaults.standard.string(forKey: Keys.epgRefreshInterval) ?? "") ?? .sixHours }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: Keys.epgRefreshInterval) }
    }

    var serverURLString: String {
        get { UserDefaults.standard.string(forKey: Keys.serverURL) ?? "http://localhost:8888" }
        set { UserDefaults.standard.set(newValue, forKey: Keys.serverURL) }
    }

    var serverURL: URL? {
        URL(string: serverURLString)
    }

    var onboardingComplete: Bool {
        get { UserDefaults.standard.bool(forKey: Keys.onboardingComplete) }
        set { UserDefaults.standard.set(newValue, forKey: Keys.onboardingComplete) }
    }

    var favouriteChannelIDs: Set<UUID> {
        get {
            let strings = UserDefaults.standard.stringArray(forKey: Keys.favourites) ?? []
            return Set(strings.compactMap { UUID(uuidString: $0) })
        }
        set {
            UserDefaults.standard.set(newValue.map(\.uuidString), forKey: Keys.favourites)
        }
    }

    // MARK: - Keys

    private enum Keys {
        static let bufferPreset       = "bufferPreset"
        static let epgURL             = "epgURL"
        static let epgRefreshInterval = "epgRefreshInterval"
        static let serverURL          = "serverURL"
        static let onboardingComplete = "onboardingComplete"
        static let favourites         = "favouriteChannelIDs"
    }

    // MARK: - Helpers

    func toggleFavourite(_ channel: Channel) {
        var favs = favouriteChannelIDs
        if favs.contains(channel.id) {
            favs.remove(channel.id)
        } else {
            favs.insert(channel.id)
        }
        favouriteChannelIDs = favs
    }

    func isFavourite(_ channel: Channel) -> Bool {
        favouriteChannelIDs.contains(channel.id)
    }
}