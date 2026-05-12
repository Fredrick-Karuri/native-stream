// EPGParser
// Parses XMLTV-format EPG data using SAX-style XMLParser for memory efficiency.
// XMLTV files can be large (100MB+) — we never load the full tree into memory.

import Foundation

// MARK: - EPG Store

struct EPGStore: Sendable {
    /// Programmes keyed by channel tvg-id.
    private let programmes: [String: [Programme]]

    init(programmes: [String: [Programme]]) {
        self.programmes = programmes
    }

    /// Currently-airing programme for a channel id.
    func currentProgramme(for channelId: String) -> Programme? {
        programmes[channelId]?.first(where: { $0.isNow })
    }

    /// Next programme (first one that starts after now).
    func nextProgramme(for channelId: String) -> Programme? {
        let now = Date()
        return programmes[channelId]?
            .filter { $0.start > now }
            .sorted { $0.start < $1.start }
            .first
    }

    /// Full schedule for a channel, sorted by start time.
    func schedule(for channelId: String) -> [Programme] {
        (programmes[channelId] ?? []).sorted { $0.start < $1.start }
    }

    var channelCount: Int { programmes.keys.count }
    var programmeCount: Int { programmes.values.reduce(0) { $0 + $1.count } }
}

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
            throw AppError.epgParseError(reason: xmlParser.parserError?.localizedDescription ?? "Unknown XML error")
        }
        return EPGStore(programmes: programmes)
    }

    func parse(url: URL) async throws -> EPGStore {
        let data: Data
        do {
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
        } catch let e as AppError {
            throw e
        } catch {
            throw AppError.epgFetchFailed(underlying: error)
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
        // XMLTV format: "20260502150000 +0000"
        return xmltvDateFormatter.date(from: string)
    }
}

// MARK: - XMLParserDelegate

extension EPGParser: XMLParserDelegate {

    func parser(_ parser: XMLParser, didStartElement element: String,
                namespaceURI: String?, qualifiedName qName: String?,
                attributes: [String: String]) {
        handleStartElement(element, attributes: attributes)
    }

    func parser(_ parser: XMLParser, didEndElement element: String,
                namespaceURI: String?, qualifiedName qName: String?) {
        handleEndElement(element)
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        appendChars(string)
    }

    private func handleStartElement(_ element: String, attributes: [String: String]) {
        switch element {
        case "programme":
            insideProgramme = true
            currentChannelId = attributes["channel"]
            currentStart = attributes["start"].flatMap(EPGParser.parseDate)
            currentStop = attributes["stop"].flatMap(EPGParser.parseDate)
            currentTitle = nil
            charBuffer = ""

        case "title" where insideProgramme:
            insideTitle = true
            charBuffer = ""

        default:
            break
        }
    }

    private func handleEndElement(_ element: String) {
        switch element {
        case "title" where insideTitle:
            currentTitle = charBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            insideTitle = false
            charBuffer = ""

        case "programme":
            if let channelId = currentChannelId,
               let title = currentTitle,
               let start = currentStart,
               let stop = currentStop {
                let programme = Programme(
                    channelId: channelId,
                    title: title,
                    start: start,
                    stop: stop
                )
                programmes[channelId, default: []].append(programme)
            }
            insideProgramme = false
            currentChannelId = nil
            currentTitle = nil
            currentStart = nil
            currentStop = nil

        default:
            break
        }
    }

    private func appendChars(_ string: String) {
        if insideTitle {
            charBuffer += string
        }
    }
}