// ChannelManagerViewModel.swift
// Exposes server channel management (add, update, delete, probe, discovery)
// to the UI via APIClient. Used by SettingsScreenV4 and future admin views.

import Foundation
import Observation

@Observable
@MainActor
final class ChannelManagerViewModel {

    var channels: [ChannelResponse] = []
    var discoveryStatus: DiscoveryStatusResponse? = nil
    var unmatched: [UnmatchedLink] = []
    var isLoading = false
    var error: String? = nil

    // MARK: - Channels

    func loadChannels() async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            channels = try await APIClient.shared.listChannels()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func addChannel(
        name: String,
        groupTitle: String,
        tvgID: String = "",
        logoURL: String = "",
        streamURL: String,
        keywords: [String]
    ) async {
        isLoading = true
        defer { isLoading = false }
        let req = CreateChannelRequest(
            name: name, groupTitle: groupTitle,
            tvgID: tvgID, logoURL: logoURL,
            streamURL: streamURL, keywords: keywords
        )
        do {
            let _ = try await APIClient.shared.createChannel(req)
            await loadChannels()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func updateStreamURL(channelID: String, url: String) async {
        do {
            try await APIClient.shared.updateChannel(
                id: channelID, UpdateChannelRequest(streamURL: url)
            )
            await loadChannels()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func deleteChannel(id: String) async {
        do {
            try await APIClient.shared.deleteChannel(id: id)
            channels.removeAll { $0.id == id }
        } catch {
            self.error = error.localizedDescription
        }
    }

    // MARK: - Probe

    func triggerProbe() async {
        do {
            try await APIClient.shared.triggerProbe()
        } catch {
            self.error = error.localizedDescription
        }
    }

    // MARK: - Discovery

    func loadDiscoveryStatus() async {
        do {
            discoveryStatus = try await APIClient.shared.discoveryStatus()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func triggerDiscovery() async {
        do {
            try await APIClient.shared.triggerDiscovery()
            await loadDiscoveryStatus()
        } catch {
            self.error = error.localizedDescription
        }
    }

    func loadUnmatched() async {
        do {
            let r = try await APIClient.shared.unmatchedLinks()
            unmatched = r.unmatched
        } catch {
            self.error = error.localizedDescription
        }
    }

    func assignUnmatched(url: String, toChannelID channelID: String) async {
        do {
            try await APIClient.shared.assignUnmatchedLink(channelID: channelID, url: url)
            unmatched.removeAll { $0.url == url }
            await loadChannels()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
