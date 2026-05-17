# NativeStream — UI Specification

**Version:** 1.0  
**Last updated:** May 2026  
**Principle:** Lean and functional. Every screen has one job.

---

## Global chrome

**Window minimum:** 960 × 580pt  
**Left rail:** 52pt wide, persistent across all screens except PlayerScreen  
**Mini player:** floating bottom-right, visible on all screens when a channel is playing except PlayerScreen  

---

## Rail

| Icon | Destination | Visibility |
|------|-------------|------------|
| `play.fill` | Now | Always |
| sport icons | Sport filter | Only when EPG has matching content |
| `star` | Favourites | Always |
| `calendar` | Schedule | Always |
| `square.grid.2x2` | All Channels | Always |
| `questionmark.circle` | Help | Always |
| `gearshape` | Settings | Always |

Active state: `NS.accentGlow` background, `NS.accentBorder` stroke, `NS.accent2` icon.

---

## Screen 1 — Now

**Job:** Show everything live right now without browsing.

### Top bar
- Title: "What's on"
- Right: live count label e.g. "11 live · 5 soon"

### Sections
All sections are hidden when empty.

**Matches live**
- `NSPulseDot` + section header with count
- One `MatchHeroCard` (full width) for the first live match
- `MatchSmallGrid` (2 columns) for remaining live matches
- Each card: two team initials circles, score placeholder, live minute, channel name, progress bar

**Live on air**
- TV icon + section header
- `LiveOnAirRow` list for non-match live content (PGA, snooker, studio shows)
- Row: 36pt square logo, programme title, channel name, progress bar, LIVE badge

**Starting soon**
- Clock icon + section header
- `StartingSoonGrid` (3 columns)
- Card: kick-off time in accent, team badges, event title, channel name

### Empty state
📺 icon + "Nothing on right now" + hint to add a playlist source.

---

## Screen 2 — Sport filter

**Job:** All channels for one sport, EPG-aware, grouped by competition.

### Top bar
- Title: sport name (e.g. "Football")
- Chips: `Live now` (default) · `All channels`
- Right: channel count

### Content
- `LazyVStack` grouped by competition
- Section header per group: `NSGroupHeader(title:, count:)`
- `ChannelCard` grid — column count driven by `NS.CardSize.minWidth = 220pt`, `GridItem(.flexible())`

### Channel card
```
┌──────────────────────────────┐
│  [LIVE badge]    [★ or ▶NOW] │  overlaid on artwork
│                              │
│     16:9 artwork / logo      │
│                              │
│  [progress bar, pinned btm]  │
├──────────────────────────────┤
│  Channel name                │  NS.Font.captionMed
│  Programme or upcoming time  │  NS.Font.caption / accent2 or text3
└──────────────────────────────┘
```
- Hover: `scaleEffect(1.02)`, `.easeOut(0.12s)`
- Playing: `NS.accentBorder` stroke 0.5pt + ▶ NOW badge
- Live: red border 0.5pt + LIVE badge

### Empty state
📅 icon + "No [sport] on right now" + hint.

---

## Screen 3 — Player

**Job:** Watch a channel. Switch channels without leaving.

### Layout
`HStack` — video (flex 1) + sidebar (230pt)

### Video area
- Black background, `AVPlayerRepresentable` fills frame
- `NS.playerTopGradient` overlay: back chevron, channel name, programme title, LIVE badge, ✕
- `NS.playerBottomGradient` overlay: progress bar + controls row
- Controls: ◀◀ · ▶/⏸ (primary) · ▶▶ · quality menu · PiP · mute · AirPlay · sidebar toggle
- Sidebar toggle icon: `arrow.up.left.and.arrow.down.right` (collapse) / reverse (expand)
- Match score overlay when EPG title contains "X vs Y" pattern

### Sidebar
Two tabs via `NSSegmentedPicker`: **On now** · **Schedule**

**On now tab**
- All channels sorted: playing → live matches → live programmes → upcoming
- Row: 32pt logo, channel name, EPG description, pulse dot or upcoming time
- Active row: `NS.accentGlow` background
- Tap any row → switches channel instantly

**Schedule tab**
- EPG timeline for current channel
- Past rows: `opacity(0.4)`
- Current row: `NS.accentGlow` bg + `NS.accentBorder` stroke + "now" label + progress bar
- Future rows: full opacity

### Fullscreen
Sidebar toggle in controls hides the 230pt sidebar, video fills window. Toggle restores it.

---

## Screen 4 — Schedule

**Job:** Browse what's on today and the next 7 days.

### Top bar
- Title: "Schedule"
- Sport filter chips: `All sports` + one per `SportCategory`

### Layout
`HStack` — date column (180pt) + event list (flex)

### Date column
- 7 rows: Today, Tomorrow, then named days
- Each row: day name, date, event count badge in accent
- Active: `NS.surface2` bg + `NS.border` stroke

### Event list
Grouped time brackets: **Live now** (with pulse dot) · **Morning** · **This afternoon** · **Tonight**

**`ScheduleEventRow`**
- Time col (44pt): kick-off or live minute in red
- Team badges: two 22pt initials circles + "vs"
- Body: event title + channel name
- Right: LIVE badge, or kick-off time in accent, or bell button
- Bell: outline = unset, filled accent = set
- Live row: red border 0.5pt

### Empty state
📅 icon + "Nothing scheduled" + hint.

---

## Screen 5 — Favourites

**Job:** Starred channels only, live first.

### Top bar
- Title: "Favourites"
- Chips: `Live now` (default) · `All`
- Right: starred count

### Sections

**Live now** — pulse dot + header  
**`FavouriteRow`:** 36pt logo, channel name, match teams or programme title, progress bar, LIVE or ▶ NOW badge, filled amber star  
- Playing: `NS.accentBorder` border  
- Live: red border  
- Star tap removes from list immediately  

**Up next** — clock icon + header  
Same row, upcoming time in accent instead of badge.

### Empty state
`star` icon (low opacity) + "No favourites yet" + "Tap the star on any channel to add it here."

---

## Screen 6 — All channels

**Job:** Full unfiltered channel list. Find anything.

### Top bar
- Title: "All Channels"
- Search input (200pt, right-aligned) — filters name and groupTitle
- Channel count

### Content
- `LazyVStack` grouped by `channel.groupTitle`, sorted alphabetically
- Pinned `NSGroupHeader` per section
- Same `ChannelCard` as Screen 2

### Empty state
📺 icon + "No channels found" + contextual hint (add source or try different search).

---

## Screen 7 — Settings

**Job:** Configure sources, playback, and server. No dead ends.

### Top bar
- Title: "Settings"

### Layout
`HStack` — sidebar (200pt, `NS.surface`) + content panel (flex, `NS.bg`)

### Sidebar
Six items with SF symbol icons: Sources · Playback · TV Guide · Server · Proxy · Discovery  
Bottom: server health card — `NSHealthDot` + status text + server URL

### Panels

**Sources** (default)  
- Playlist sources list: health dot, label, URL, refresh interval, trash button  
- Add source button (dashed border)  
- EPG / TV Guide source row below a divider  

**Playback**  
- Buffer preset: `NSSegmentedPicker` bound to `BufferPreset`  
- Hardware decoding: `NSToggle` (disabled, always on for Apple Silicon)  

**TV Guide**  
- EPG URL text field  
- Refresh interval picker  

**Server**  
- Server URL text field  
- Quick start code blocks  

**Proxy**  
- Enable proxy toggle  
- Referer + User-Agent fields (shown when enabled)  

**Discovery**  
- Enable toggle  
- Config file hint + code block (shown when enabled)  

---

## Screen 8 — Help

**Job:** Answer questions without leaving the app.

### Top bar
- Tab picker: `User Guide` · `Developer`
- Search input (180pt)

### Layout
`HStack` — section list (180pt, `NS.surface`) + content area (flex)

### Section list
Sidebar of named sections. Active: `NS.accentGlow` bg.  
Filters reactively as search text changes.

### Content area
Each section contains `HelpItem` entries with four block types:
- **Text** — body prose, `NS.text2`
- **Code** — `NSCodeBlock` with copy button
- **Tip** — amber lightbulb callout
- **Warning** — red triangle callout

Items separated by a hairline rule.

### User Guide sections
Getting started · Navigation · Playing channels · Favourites · Schedule · Settings

### Developer sections
Architecture · Design system · View hierarchy · Data flow · StreamServer · Contributing

### Empty state
Search icon + "No results for '…'" when search yields nothing.

---

## Component quick reference

| Component | Use |
|-----------|-----|
| `ChannelCard` | Sport filter + All channels grids |
| `ChannelLogoView` | Inside ChannelCard (16:9) |
| `ChannelLogoSquare` | Rows and lists (square, configurable size) |
| `MatchHeroCard` | Now screen — featured live match |
| `MatchSmallCard` | Now screen — remaining live matches |
| `LiveOnAirRow` | Now screen — non-match live content |
| `StartingSoonCard` | Now screen — upcoming events |
| `FavouriteRow` | Favourites screen |
| `ScheduleEventRow` | Schedule screen |
| `PlayerSidebarRow` | Player on-now tab |
| `NSLiveBadge` | Red pulsing LIVE pill |
| `NSProgressBar` | Progress indication |
| `NSToggle` | Settings toggles |
| `NSChip` | Filter chips |
| `NSGroupHeader` | Section headers |
| `NSCodeBlock` | Monospace code with copy |
| `NSHealthDot` | Server / source health indicator |
| `NSPulseDot` | Live section pulse indicator |
| `ChannelLogoSquare` | 36pt logo in rows |

---

## What this app does not do

- No VOD or catch-up — live only
- No in-app stream search or discovery UI — server handles that
- No notifications (bell UI is stubbed, implementation pending)
- No user accounts or sync
- No ads