// FavouritesManager.swift — FX-010
// FX-010: favouriteIDs is now a stored @Observable property.
// Loaded once from UserDefaults in init(). Written back on toggle().
// @Observable observes the stored property directly — cross-component
// updates propagate correctly without repeated UserDefaults reads.

import Foundation
import Observation

@Observable
final class FavouritesManager {

    private let key = "favouriteChannelIDs"

    // Stored — not computed. @Observable tracks mutations here.
    var favouriteIDs: Set<String>

    init() {
        let stored = UserDefaults.standard.stringArray(forKey: key) ?? []
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
        UserDefaults.standard.set(Array(favouriteIDs), forKey: key)
    }
}
