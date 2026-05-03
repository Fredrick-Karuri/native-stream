// NS-021 + NS-023: M3UParser
// Pure Swift M3U/M3U8 playlist parser. No third-party dependencies.
// Supports local file:// and remote https:// sources.

import Foundation

// MARK: - Parser warnings (non-fatal)

struct M3UParseWarning: Sendable {
    let line: Int
    let reason: String
}

// MARK: - Parser

actor M3UParser {

    // MARK: - Public API

    /// Parse from raw Data (used in unit tests without network).
    func parse(data: Data) throws -> (channels: [Channel], warnings: [M3UParseWarning]) {
        guard let text = String(data: data, encoding: .utf8) ??
                         String(data: data, encoding: .isoLatin1) else {
            throw AppError.playlistParseError(line: 0, reason: "File is not valid UTF-8 or Latin-1 text")
        }
        return parseText(text)
    }

    /// Parse from a local file:// or remote https:// URL.
    func parse(url: URL) async throws -> (channels: [Channel], warnings: [M3UParseWarning]) {
        let data: Data
        do {
            if url.isFileURL {
                data = try Data(contentsOf: url)
            } else {
                let (fetched, response) = try await URLSession.shared.data(from: url)
                guard let http = response as? HTTPURLResponse,
                      (200...299).contains(http.statusCode) else {
                    throw AppError.playlistFetchFailed(
                        url: url,
                        underlying: URLError(.badServerResponse)
                    )
                }
                data = fetched
            }
        } catch let error as AppError {
            throw error
        } catch {
            throw AppError.playlistFetchFailed(url: url, underlying: error)
        }
        return try parse(data: data)
    }

    // MARK: - Internal parsing

    private func parseText(_ text: String) -> (channels: [Channel], warnings: [M3UParseWarning]) {
        var channels: [Channel] = []
        var warnings: [M3UParseWarning] = []
        let lines = text.components(separatedBy: .newlines)

        var lineIndex = 0
        var pendingMeta: ChannelMetadata? = nil

        for (i, rawLine) in lines.enumerated() {
            lineIndex = i + 1
            let line = rawLine.trimmingCharacters(in: .whitespaces)

            if line.isEmpty { continue }

            if i == 0 && line.hasPrefix("#EXTM3U") {
                // Valid header — continue
                continue
            }

            if line.hasPrefix("#EXTINF:") {
                pendingMeta = parseExtInf(line, lineNumber: lineIndex, warnings: &warnings)
                continue
            }

            if line.hasPrefix("#") {
                // Other comment/directive — skip
                continue
            }

            // This should be a stream URL
            guard let streamURL = URL(string: line), streamURL.scheme != nil else {
                warnings.append(M3UParseWarning(line: lineIndex, reason: "Invalid stream URL: \(line)"))
                pendingMeta = nil
                continue
            }

            let meta = pendingMeta ?? ChannelMetadata(name: "Channel \(channels.count + 1)")
            channels.append(Channel(
                tvgId: meta.tvgId,
                name: meta.name,
                groupTitle: meta.groupTitle,
                logoURL: meta.logoURL,
                streamURL: streamURL
            ))
            pendingMeta = nil
        }

        return (channels, warnings)
    }

    // MARK: - EXTINF parsing

    private struct ChannelMetadata {
        var tvgId: String = ""
        var name: String
        var groupTitle: String = "Uncategorised"
        var logoURL: URL? = nil
    }

    private func parseExtInf(
        _ line: String,
        lineNumber: Int,
        warnings: inout [M3UParseWarning]
    ) -> ChannelMetadata? {
        // Format: #EXTINF:-1 tvg-id="ID" tvg-logo="URL" group-title="Group",Channel Name
        // The display name is after the last comma

        guard let commaRange = line.range(of: ",", options: .backwards) else {
            warnings.append(M3UParseWarning(line: lineNumber, reason: "EXTINF missing comma separator"))
            return nil
        }

        let name = String(line[commaRange.upperBound...]).trimmingCharacters(in: .whitespaces)
        let attrSection = String(line[line.startIndex..<commaRange.lowerBound])

        var meta = ChannelMetadata(name: name.isEmpty ? "Unknown Channel" : name)

        // Extract tvg-id
        if let value = extractAttribute("tvg-id", from: attrSection) {
            meta.tvgId = value
        }

        // Extract group-title
        if let value = extractAttribute("group-title", from: attrSection) {
            meta.groupTitle = value.isEmpty ? "Uncategorised" : value
        }

        // Extract tvg-logo
        if let value = extractAttribute("tvg-logo", from: attrSection),
           let logoURL = URL(string: value) {
            meta.logoURL = logoURL
        }

        return meta
    }

    /// Extracts a quoted attribute value from EXTINF attribute string.
    /// Handles both double-quoted and unquoted values.
    private func extractAttribute(_ key: String, from string: String) -> String? {
        // Match key="value" or key='value'
        let patterns = [
            "\(key)=\"([^\"]*)\"",
            "\(key)='([^']*)'",
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: string, range: NSRange(string.startIndex..., in: string)),
               let range = Range(match.range(at: 1), in: string) {
                return String(string[range])
            }
        }
        return nil
    }
}