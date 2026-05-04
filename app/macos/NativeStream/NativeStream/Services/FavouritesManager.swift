// FavouritesManager.swift — NS-311
// Manages favourite channels, persisted to UserDefaults.

import Foundation
import Observation

@Observable
final class FavouritesManager {

    private let key = "favouriteChannelIDs"

    var favouriteIDs: Set<UUID> {
        get {
            let strings = UserDefaults.standard.stringArray(forKey: key) ?? []
            return Set(strings.compactMap { UUID(uuidString: $0) })
        }
        set {
            UserDefaults.standard.set(newValue.map(\.uuidString), forKey: key)
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