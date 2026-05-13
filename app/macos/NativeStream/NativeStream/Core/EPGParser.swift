// EPGParser.swift — FX-001
// Parses XMLTV-format EPG data using SAX-style XMLParser for memory efficiency.
// XMLTV files can be large (100MB+) — we never load the full tree into memory.
//
// FX-001: Was an `actor` with nonisolated delegate methods firing fire-and-forget
// Tasks. xmlParser.parse() returned before any Tasks ran → empty EPGStore.
// Fix: plain `final class`. Delegate mutates state synchronously on the parser
// thread. EPGViewModel runs parse() in Task.detached for background execution.

import Foundation


// MARK: - Parser

final class EPGParser: NSObject {

    private var programmes: [String: [Programme]] = [:]
    private var currentChannelId: String?
    private var currentTitle: String?
    private var currentStart: Date?
    private var currentStop: Date?
    private var insideProgramme = false
    private var insideTitle = false
    private var charBuffer = ""

    private static let xmltvDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyyMMddHHmmss Z"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    // MARK: - Public API

    func parse(data: Data) throws -> EPGStore {
        reset()
        let xmlParser = XMLParser(data: data)
        xmlParser.delegate = self
        guard xmlParser.parse() else {
            throw AppError.epgParseError(
                reason: xmlParser.parserError?.localizedDescription ?? "Unknown XML error"
            )
        }
        return EPGStore(programmes: programmes)
    }

    func parse(url: URL) async throws -> EPGStore {
        let data: Data
        if url.isFileURL {
            data = try Data(contentsOf: url)
        } else {
            let (fetched, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse,
                  (200...299).contains(http.statusCode) else {
                throw AppError.epgFetchFailed(underlying: URLError(.badServerResponse))
            }
            data = fetched
        }
        return try parse(data: data)
    }

    // MARK: - Internal

    private func reset() {
        programmes = [:]
        currentChannelId = nil
        currentTitle = nil
        currentStart = nil
        currentStop = nil
        insideProgramme = false
        insideTitle = false
        charBuffer = ""
    }

    private static func parseDate(_ string: String) -> Date? {
        xmltvDateFormatter.date(from: string)
    }
}

// MARK: - XMLParserDelegate
// Delegate methods are called synchronously by XMLParser on the calling thread.
// No async dispatch needed — state is mutated directly.

extension EPGParser: XMLParserDelegate {

    func parser(
        _ parser: XMLParser,
        didStartElement element: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes: [String: String] = [:]
    ) {
        switch element {
        case "programme":
            insideProgramme  = true
            currentChannelId = attributes["channel"]
            currentStart     = attributes["start"].flatMap(EPGParser.parseDate)
            currentStop      = attributes["stop"].flatMap(EPGParser.parseDate)
            currentTitle     = nil
            charBuffer       = ""

        case "title" where insideProgramme:
            insideTitle = true
            charBuffer  = ""

        default:
            break
        }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement element: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        switch element {
        case "title" where insideTitle:
            currentTitle = charBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            insideTitle  = false
            charBuffer   = ""

        case "programme":
            if let channelId = currentChannelId,
               let title     = currentTitle,
               let start     = currentStart,
               let stop      = currentStop {
                programmes[channelId, default: []].append(
                    Programme(channelId: channelId, title: title, start: start, stop: stop)
                )
            }
            insideProgramme  = false
            currentChannelId = nil
            currentTitle     = nil
            currentStart     = nil
            currentStop      = nil

        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideTitle { charBuffer += string }
    }
}
