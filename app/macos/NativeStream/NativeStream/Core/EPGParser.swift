// EPGParser.swift — FX-001, FX-002
import Foundation

// MARK: - Parser (FX-001: plain final class, no actor)

final class EPGParser: NSObject {

    private var programmes: [String: [Programme]] = [:]
    private var currentChannelId: String?
    private var currentTitle: String?
    private var currentStart: Date?
    private var currentStop: Date?
    private var insideProgramme = false
    private var insideTitle = false
    private var charBuffer = ""
    
    private static func parseDate(_ string: String) -> Date? {
        // "20240607143000 +0000" — 15 chars min
        guard string.count >= 15 else { return nil }
        let s = string
        guard let year  = Int(s.prefix(4)),
              let month = Int(s.dropFirst(4).prefix(2)),
              let day   = Int(s.dropFirst(6).prefix(2)),
              let hour  = Int(s.dropFirst(8).prefix(2)),
              let min   = Int(s.dropFirst(10).prefix(2)),
              let sec   = Int(s.dropFirst(12).prefix(2)) else { return nil }

        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = day
        comps.hour = hour; comps.minute = min;  comps.second = sec
        comps.timeZone = parseTimezone(String(s.dropFirst(15).trimmingCharacters(in: .whitespaces)))
        return Calendar(identifier: .gregorian).date(from: comps)
    }

    private static func parseTimezone(_ offset: String) -> TimeZone {
        // "+0200", "-0530", "UTC", ""
        guard offset.count == 5,
              let sign = offset.first, sign == "+" || sign == "-",
              let h = Int(offset.dropFirst(1).prefix(2)),
              let m = Int(offset.dropFirst(3).prefix(2)) else {
            return TimeZone(identifier: "UTC")!
        }
        let seconds = (h * 3600 + m * 60) * (sign == "+" ? 1 : -1)
        return TimeZone(secondsFromGMT: seconds) ?? TimeZone(identifier: "UTC")!
    }


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

    private func reset() {
        programmes = [:]
        currentChannelId = nil; currentTitle = nil
        currentStart = nil; currentStop = nil
        insideProgramme = false; insideTitle = false
        charBuffer = ""
    }

}

// MARK: - XMLParserDelegate (synchronous — no Task dispatch)

extension EPGParser: XMLParserDelegate {

    func parser(_ parser: XMLParser, didStartElement element: String,
                namespaceURI: String?, qualifiedName: String?,
                attributes: [String: String] = [:]) {
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
        default: break
        }
    }

    func parser(_ parser: XMLParser, didEndElement element: String,
                namespaceURI: String?, qualifiedName: String?) {
        switch element {
        case "title" where insideTitle:
            currentTitle = charBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            insideTitle  = false
            charBuffer   = ""
        case "programme":
            if let id = currentChannelId, let title = currentTitle,
               let start = currentStart, let stop = currentStop {
                programmes[id, default: []].append(
                    Programme(channelId: id, title: title, start: start, stop: stop)
                )
            }
            insideProgramme  = false
            currentChannelId = nil; currentTitle = nil
            currentStart     = nil; currentStop  = nil
        default: break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideTitle { charBuffer += string }
    }
}
