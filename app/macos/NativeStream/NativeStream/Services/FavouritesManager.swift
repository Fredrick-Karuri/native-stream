// FavouritesManager.swift — NS-311
// Manages favourite channels, persisted to UserDefaults.

import Foundation
import Observation

@Observable
final class FavouritesManager {

    private let key = "favouriteChannelIDs"

    var favouriteIDs: Set<String> {
        get {
            Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
        }
        set {
            UserDefaults.standard.set(Array(newValue), forKey: key)
        }
    }

    func toggle(_ channel: Channel) {
        var ids = favouriteIDs
        if ids.contains(channel.id) { ids.remove(channel.id) } else { ids.insert(channel.id) }
        favouriteIDs = ids
    }

    func isFavourite(_ channel: Channel) -> Bool {
        favouriteIDs.contains(channel.id)
    }

    func favourites(from channels: [Channel]) -> [Channel] {
        let ids = favouriteIDs
        return channels.filter { ids.contains($0.id) }
    }
}
