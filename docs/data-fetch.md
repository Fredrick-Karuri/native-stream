# NativeStream — Data Fetch Architecture

**Version:** 1.1  
**Last updated:** May 2026  
**Principle:** All data sources are protocol-backed. ESPN, Pluto TV, and XMLTV are reference implementations — swap without touching UI.

---

## Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                          NativeStream                           │
│                                                                 │
│   ┌─────────────────────┐      ┌──────────────────────────┐    │
│   │  MatchDataProvider  │      │  ChannelDataProvider     │    │
│   │  (protocol)         │      │  (protocol)              │    │
│   │                     │      │                          │    │
│   │  ESPNProvider ─────▶│      │  M3UProvider ───────────▶│    │
│   │  (reference impl)   │      │  (reference impl)        │    │
│   └──────────┬──────────┘      └────────────┬─────────────┘    │
│              │                              │                   │
│              └──────────────┬───────────────┘                   │
│                             │                                   │
│                    ┌────────▼────────┐                          │
│                    │   DataStore     │  @Observable              │
│                    │  (in-memory)    │                          │
│                    └────────┬────────┘                          │
│                             │                                   │
│         ┌───────────────────┼───────────────────┐              │
│         ▼                   ▼                   ▼              │
│    NowScreen           PlayerScreen        ScheduleScreen       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Protocols

### MatchDataProvider

Supplies live scores, fixtures, and match metadata.

```swift
protocol MatchDataProvider {
    /// Fetch all events across configured sports/leagues.
    func fetchEvents() async throws -> [MatchEvent]

    /// Poll a single live event for score updates.
    func fetchLiveEvent(id: String) async throws -> MatchEvent
}

struct MatchEvent: Identifiable {
    let id: String
    let title: String           // "Arsenal vs Chelsea"
    let shortName: String       // "ARS vs CHE"
    let sport: SportCategory
    let league: String
    let startDate: Date
    let state: MatchState       // .pre | .live | .post
    let clock: String?          // "67'" — nil for pre/post
    let period: Int?
    let homeTeam: MatchTeam
    let awayTeam: MatchTeam
}

struct MatchTeam {
    let abbreviation: String
    let displayName: String
    let logoURL: URL?
    let color: Color?
    let score: String?          // nil when pre
}

enum MatchState { case pre, live, post }
```

### ChannelDataProvider

Supplies the channel list from any playlist source.

```swift
protocol ChannelDataProvider {
    func fetchChannels(from sources: [PlaylistSource]) async throws -> [Channel]
}

struct Channel: Identifiable {
    let id: String              // tvg-id or equivalent
    let name: String
    let logoURL: URL?
    let groupTitle: String
    let streamURL: URL
    var epgPrograms: [EPGProgram] = []
}
```

### EPGProvider

Supplies programme schedule data.

```swift
protocol EPGProvider {
    func fetchPrograms(epgURL: URL) async throws -> [EPGProgram]
}

struct EPGProgram: Identifiable {
    let id: String
    let channelID: String       // joins to Channel.id
    let title: String
    let description: String?
    let start: Date
    let stop: Date
    let category: String?

    var isLive: Bool { (start...stop).contains(Date()) }
    var progress: Double { Date().timeIntervalSince(start) / stop.timeIntervalSince(start) }
}
```

---

## Reference Implementations

### ESPNProvider: MatchDataProvider

```swift
struct ESPNProvider: MatchDataProvider {
    var leagues: [ESPNLeague] = ESPNLeague.defaults

    func fetchEvents() async throws -> [MatchEvent] {
        try await withThrowingTaskGroup(of: [MatchEvent].self) { group in
            for league in leagues {
                group.addTask { try await fetchScoreboard(league) }
            }
            return try await group.reduce(into: []) { $0 += $1 }
        }
    }

    func fetchLiveEvent(id: String) async throws -> MatchEvent {
        // GET /summary?event={id}
    }
}

struct ESPNLeague {
    let sport: String   // "soccer"
    let slug: String    // "eng.1"

    // https://site.api.espn.com/apis/site/v2/sports/{sport}/{slug}/scoreboard
    // Schedule range: append ?dates=YYYYMMDD-YYYYMMDD

    static let defaults: [ESPNLeague] = [
        .init(sport: "soccer",     slug: "eng.1"),
        .init(sport: "soccer",     slug: "uefa.champions"),
        .init(sport: "basketball", slug: "nba"),
        .init(sport: "football",   slug: "nfl"),
        .init(sport: "hockey",     slug: "nhl"),
        .init(sport: "baseball",   slug: "mlb"),
        .init(sport: "tennis",     slug: "atp"),
        .init(sport: "golf",       slug: "pga"),
        .init(sport: "mma",        slug: "ufc"),
    ]
}
```

ESPN `status.type.state` → `MatchState`:

| ESPN value | MatchState |
|------------|------------|
| `"pre"` | `.pre` |
| `"in"` | `.live` |
| `"post"` | `.post` |

### M3UProvider: ChannelDataProvider

```swift
struct M3UProvider: ChannelDataProvider {
    func fetchChannels(from sources: [PlaylistSource]) async throws -> [Channel] {
        try await withThrowingTaskGroup(of: [Channel].self) { group in
            for source in sources {
                group.addTask { try await fetchAndParse(source.url) }
            }
            return try await group.reduce(into: []) { $0 += $1 }
        }
    }
    // Parses #EXTINF tags: tvg-id, tvg-name, tvg-logo, group-title
    // Stream URL = line immediately following #EXTINF
}
```

### XMLTVProvider: EPGProvider

```swift
struct XMLTVProvider: EPGProvider {
    func fetchPrograms(epgURL: URL) async throws -> [EPGProgram] {
        // Fetch XML, parse <programme> elements
        // Join key: <programme channel="..."> → Channel.id (via tvg-id)
    }
}
```

---

## DataStore

```swift
@Observable
class DataStore {
    // Injected providers — swap at any time, no view changes required
    var matchProvider: any MatchDataProvider = ESPNProvider()
    var channelProvider: any ChannelDataProvider = M3UProvider()
    var epgProvider: any EPGProvider = XMLTVProvider()

    // State
    var channels: [Channel] = []
    var events: [MatchEvent] = []
    var sourceHealth: SourceHealth = .init()

    // Derived — consumed directly by screens
    var liveEvents: [MatchEvent]     { events.filter { $0.state == .live } }
    var upcomingEvents: [MatchEvent] { events.filter { $0.state == .pre } }
    var liveChannels: [Channel]      { channels.filter { $0.currentProgram?.isLive == true } }
    var sportCategories: [SportCategory] { /* union of event leagues + channel groupTitles */ }

    func loadAll() async { /* fetch all three sources concurrently */ }
    func refreshMatches() async { /* called on poll timer */ }
}
```

### SourceHealth → Settings UI

Maps directly to `NSHealthDot` scores used in `SourceRow` and `EPGSourceRow`:

| Condition | score | Display |
|-----------|-------|---------|
| Fetched within interval | `1.0` | Green dot |
| Stale (> 2× interval) | `0.3` | Amber dot + "stale" |
| Fetch error | `0.0` | Red dot |

---

## Now screen merge logic

Links ESPN events to Pluto channels so `MatchHeroCard` can carry a playable stream:

```
liveEvents
    │
    for each event:
    ├── channel.currentProgram.title fuzzy-matches event.shortName?
    │       yes → MatchCard(event:, channel:)   // score + play button
    │       no  → MatchCard(event:, channel: nil) // score only
```

Fuzzy match: lowercase + strip punctuation, confirm all team abbreviations present in title.

---

## Poll intervals

| Data | Interval | Configured by |
|------|----------|---------------|
| Match scores (live game) | 30s | Hardcoded |
| Match scores (no live game) | 60s | Hardcoded |
| Channel list | Per `PlaylistSource.refreshInterval` | `SourcesSection` UI |
| EPG | 12h default | `TVGuideSection` UI |
| Full refresh on foreground | On resume | ScenePhase |

---

## Swapping a provider

No view code changes. Example — replace ESPN with a self-hosted scores API:

```swift
let store = DataStore()
store.matchProvider = MyScoresProvider(baseURL: settings.serverURL)
```

Add a new playlist format (e.g. JSON discovery):

```swift
struct JSONPlaylistProvider: ChannelDataProvider {
    func fetchChannels(from sources: [PlaylistSource]) async throws -> [Channel] { ... }
}
store.channelProvider = JSONPlaylistProvider()
```

`SourcesSection`, `SourceRow` health dots, and all screens are unaffected.

---

## Screen → provider dependency map

| Screen | MatchDataProvider | ChannelDataProvider | EPGProvider |
|--------|:-----------------:|:-------------------:|:-----------:|
| Now | ✅ live + upcoming | ✅ logos, stream URLs | ✅ live programmes |
| Sport filter | ✅ league grouping | ✅ channels by group | ✅ current programme |
| Player | ✅ score overlay | ✅ stream URL | ✅ schedule sidebar |
| Schedule | ✅ fixtures (all states) | — | ✅ time brackets |
| Favourites | — | ✅ starred channels | ✅ current programme |
| All Channels | — | ✅ full list | ✅ current programme |
| Settings → Sources | — | health only | health only |