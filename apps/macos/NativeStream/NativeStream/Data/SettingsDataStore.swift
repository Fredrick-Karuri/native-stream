/// NativeStream/NativeStream/Data/SettingsDataStore.swift
///
/// Owns all disk persistence for playlist sources and the channel cache.
/// No parsing, no networking, no view-facing state — purely reads and
/// writes JSON to Application Support. Mirrors the Android `SettingsDataStore`.

import Foundation

protocol SettingsPersisting: Sendable {
    func loadSources() async -> [PlaylistSource]
    func saveSources(_ sources: [PlaylistSource]) async
    func loadChannels() async -> [Channel]
    func saveChannels(_ channels: [Channel]) async
    func deleteAll() async
}

actor SettingsDataStore: SettingsPersisting {

    // MARK: - Locations

    private var applicationSupportDirectory: URL {
        FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("NativeStream", isDirectory: true)
    }

    private var sourcesURL: URL {
        applicationSupportDirectory.appendingPathComponent("playlist_sources.json")
    }

    private var channelCacheURL: URL {
        applicationSupportDirectory.appendingPathComponent("channel_cache.json")
    }

    // MARK: - Sources

    func loadSources() async -> [PlaylistSource] {
        guard let data = try? Data(contentsOf: sourcesURL),
              let loaded = try? JSONDecoder().decode([PlaylistSource].self, from: data)
        else { return [] }
        return loaded
    }

    func saveSources(_ sources: [PlaylistSource]) async {
        do {
            try createStorageDirectoryIfNeeded()
            let data = try JSONEncoder().encode(sources)
            try data.write(to: sourcesURL, options: .atomic)
        } catch {
            print("⚠️ Failed to save playlist sources: \(error)")
        }
    }

    // MARK: - Channel cache

    func loadChannels() async -> [Channel] {
        guard let data = try? Data(contentsOf: channelCacheURL),
              let loaded = try? JSONDecoder().decode([Channel].self, from: data)
        else { return [] }
        return loaded
    }

    func saveChannels(_ channels: [Channel]) async {
        do {
            try createStorageDirectoryIfNeeded()
            let data = try JSONEncoder().encode(channels)
            try data.write(to: channelCacheURL, options: .atomic)
        } catch {
            print("⚠️ Failed to save channel cache: \(error)")
        }
    }

    // MARK: - Reset

    func deleteAll() async {
        try? FileManager.default.removeItem(at: sourcesURL)
        try? FileManager.default.removeItem(at: channelCacheURL)
    }

    // MARK: - Helpers

    private func createStorageDirectoryIfNeeded() throws {
        try FileManager.default.createDirectory(
            at: applicationSupportDirectory,
            withIntermediateDirectories: true
        )
    }
}
