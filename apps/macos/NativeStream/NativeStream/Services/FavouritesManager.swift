// FavouritesManager.swift

import Foundation
import Observation

@Observable
final class FavouritesManager {

    private static let favouriteChannelIDsKey = "favouriteChannelIDs"

    private let defaults: UserDefaults

    // Stored — not computed. @Observable tracks mutations here.
    var favouriteIDs: Set<String>

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = defaults.stringArray(forKey: Self.favouriteChannelIDsKey) ?? []
        favouriteIDs = Set(stored)
    }
    func toggle(_ channel: Channel) {
        if favouriteIDs.contains(channel.id) {
            favouriteIDs.remove(channel.id)
        } else {
            favouriteIDs.insert(channel.id)
        }
        persist()
    }

    func isFavourite(_ channel: Channel) -> Bool {
        favouriteIDs.contains(channel.id)
    }

    func favourites(from channels: [Channel]) -> [Channel] {
        channels.filter { favouriteIDs.contains($0.id) }
    }

    private func persist() {
        defaults.set(Array(favouriteIDs), forKey: Self.favouriteChannelIDsKey)
    }
}
