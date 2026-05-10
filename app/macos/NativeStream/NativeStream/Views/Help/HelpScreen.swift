// HelpScreen.swift
// In-app help panel. Two tabs: User Guide and Developer Reference.
// Accessible from the ? icon at the bottom of SportNavRail.

import SwiftUI

// MARK: - Help Screen

struct HelpScreen: View {

    enum HelpTab { case userGuide, developerRef }

    @State private var tab: HelpTab = .userGuide
    @State private var searchText = ""

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            HStack(spacing: 0) {
                sectionList
                Divider().overlay(NS.border)
                contentArea
            }
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: NS.Spacing.md) {
            // Tab picker
            HStack(spacing: 2) {
                helpTab("User Guide",   tab: .userGuide)
                helpTab("Developer",    tab: .developerRef)
            }
            .padding(3)
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))

            Spacer()

            // Search
            HStack(spacing: NS.Spacing.xs) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(NS.text3)
                TextField("Search…", text: $searchText)
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text)
                    .textFieldStyle(.plain)
                    .frame(width: 180)
            }
            .padding(.horizontal, NS.Spacing.md)
            .frame(height: 28)
            .background(NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    private func helpTab(_ label: String, tab: HelpTab) -> some View {
        Button { self.tab = tab } label: {
            Text(label)
                .font(NS.Font.captionMed)
                .foregroundStyle(self.tab == tab ? NS.accent2 : NS.text3)
                .padding(.horizontal, NS.Spacing.md)
                .frame(height: 26)
                .background(self.tab == tab ? NS.accentGlow : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                .overlay(
                    RoundedRectangle(cornerRadius: NS.Radius.sm)
                        .stroke(self.tab == tab ? NS.accentBorder : Color.clear, lineWidth: 0.5)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Sections sidebar

    @State private var selectedSection: String = ""

    private var sections: [HelpSection] {
        let all = tab == .userGuide ? userGuideSections : developerSections
        guard !searchText.isEmpty else { return all }
        return all.compactMap { section in
            let filteredItems = section.items.filter {
                $0.title.localizedCaseInsensitiveContains(searchText) ||
                $0.body.localizedCaseInsensitiveContains(searchText)
            }
            guard !filteredItems.isEmpty else { return nil }
            return HelpSection(title: section.title, icon: section.icon, items: filteredItems)
        }
    }

    private var sectionList: some View {
        ScrollView {
            VStack(spacing: 2) {
                ForEach(sections, id: \.title) { section in
                    Button {
                        selectedSection = section.title
                    } label: {
                        HStack(spacing: NS.Spacing.sm) {
                            Image(systemName: section.icon)
                                .font(.system(size: 13))
                                .foregroundStyle(selectedSection == section.title ? NS.accent2 : NS.text3)
                                .frame(width: 16)
                            Text(section.title)
                                .font(NS.Font.captionMed)
                                .foregroundStyle(selectedSection == section.title ? NS.accent2 : NS.text2)
                            Spacer()
                        }
                        .padding(.horizontal, NS.Spacing.sm)
                        .frame(height: 34)
                        .background(selectedSection == section.title ? NS.accentGlow : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                        .overlay(
                            RoundedRectangle(cornerRadius: NS.Radius.md)
                                .stroke(selectedSection == section.title ? NS.accentBorder : Color.clear, lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(NS.Spacing.sm)
        }
        .frame(width: 180)
        .background(NS.surface)
        .onAppear {
            if selectedSection.isEmpty, let first = sections.first {
                selectedSection = first.title
            }
        }
        .onChange(of: tab) {
            selectedSection = sections.first?.title ?? ""
        }
        .onChange(of: searchText) {
            if let first = sections.first { selectedSection = first.title }
        }
    }

    // MARK: - Content area

    private var contentArea: some View {
        ScrollView {
            if let section = sections.first(where: { $0.title == selectedSection }) {
                VStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                    ForEach(section.items, id: \.title) { item in
                        HelpItemView(item: item, search: searchText)
                    }
                }
                .padding(NS.Spacing.xxl)
            } else {
                VStack(spacing: NS.Spacing.md) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 28))
                        .foregroundStyle(NS.text3)
                    Text("No results for \"\(searchText)\"")
                        .font(NS.Font.display)
                        .foregroundStyle(NS.text)
                    Text("Try different keywords.")
                        .font(NS.Font.caption)
                        .foregroundStyle(NS.text3)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.top, 80)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Help Item View

struct HelpItemView: View {
    let item: HelpItem
    let search: String

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.md) {
            Text(item.title)
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            ForEach(item.blocks, id: \.id) { block in
                switch block.kind {
                case .text:
                    Text(block.content)
                        .font(NS.Font.body)
                        .foregroundStyle(NS.text2)
                        .fixedSize(horizontal: false, vertical: true)
                case .code:
                    NSCodeBlock(code: block.content)
                case .tip:
                    HStack(alignment: .top, spacing: NS.Spacing.sm) {
                        Image(systemName: "lightbulb.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(NS.amber)
                            .padding(.top, 2)
                        Text(block.content)
                            .font(NS.Font.caption)
                            .foregroundStyle(NS.text2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(NS.Spacing.md)
                    .background(NS.amber.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.amber.opacity(0.15), lineWidth: 0.5))
                case .warning:
                    HStack(alignment: .top, spacing: NS.Spacing.sm) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(NS.live)
                            .padding(.top, 2)
                        Text(block.content)
                            .font(NS.Font.caption)
                            .foregroundStyle(NS.text2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(NS.Spacing.md)
                    .background(NS.live.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.live.opacity(0.15), lineWidth: 0.5))
                }
            }
        }
        .padding(.bottom, NS.Spacing.md)
        .overlay(alignment: .bottom) {
            Rectangle().fill(NS.border).frame(height: 0.5)
        }
    }
}

// MARK: - Content model

struct HelpSection {
    let title: String
    let icon: String
    let items: [HelpItem]
}

struct HelpItem {
    let title: String
    let blocks: [HelpBlock]

    // Convenience for items with only a body
    var body: String { blocks.map(\.content).joined(separator: " ") }
}

struct HelpBlock: Identifiable {
    let id = UUID()
    let kind: Kind
    let content: String

    enum Kind { case text, code, tip, warning }

    static func text(_ s: String) -> HelpBlock { .init(kind: .text, content: s) }
    static func code(_ s: String) -> HelpBlock { .init(kind: .code, content: s) }
    static func tip(_ s: String)  -> HelpBlock { .init(kind: .tip,  content: s) }
    static func warn(_ s: String) -> HelpBlock { .init(kind: .warning, content: s) }
}

// MARK: - User guide content

private let userGuideSections: [HelpSection] = [
    HelpSection(title: "Getting started", icon: "play.circle", items: [
        HelpItem(title: "What is NativeStream?", blocks: [
            .text("NativeStream is a live sports TV client for macOS. It plays IPTV streams from a playlist you configure, with an EPG guide showing what's on every channel right now."),
            .text("Every channel is always tunable — the EPG simply tells you what is broadcasting at any moment. Channels like NBA TV are always on even when no game is live; you'll see what show is currently airing instead."),
            .tip("Think of NativeStream like a premium remote control for live sport, not a file browser or VOD app.")
        ]),
        HelpItem(title: "First launch", blocks: [
            .text("On first launch, NativeStream has no channels loaded. You need to add a playlist source and an EPG source in Settings."),
            .text("Open Settings from the gear icon at the bottom of the left rail. Add your M3U playlist URL under Playlist Sources, then add an XMLTV EPG URL under EPG / TV Guide."),
            .text("NativeStream will load both automatically and refresh them on the interval you choose.")
        ])
    ]),
    HelpSection(title: "Navigation", icon: "sidebar.left", items: [
        HelpItem(title: "The left rail", blocks: [
            .text("The left rail is the primary navigation. At the top is the Now button — your home screen showing everything live right now across all channels."),
            .text("Below that are sport icons. These are dynamic — they only appear when at least one channel has live or upcoming content matching that sport. Sports with live content float to the top."),
            .text("At the bottom: Favourites, Schedule, All Channels, Settings, and Help.")
        ]),
        HelpItem(title: "What's on (Now screen)", blocks: [
            .text("The Now screen is the default view. It shows three sections: Matches live (scoreable events), Live on air (PGA coverage, snooker, studio shows, etc.), and Starting soon (events beginning within 2 hours)."),
            .tip("If you want to watch PGA or snooker, they'll appear in Live on air even though they're not football matches.")
        ]),
        HelpItem(title: "Sport filter screens", blocks: [
            .text("Tapping a sport icon in the rail shows all channels whose current EPG programme matches that sport. Channels are grouped by competition — Premier League, Champions League, La Liga etc."),
            .text("This uses EPG programme titles to decide sport, not the channel's group tag. So Sky Sports Golf appears under Golf when it's showing PGA Tour Live.")
        ])
    ]),
    HelpSection(title: "Playing channels", icon: "play.rectangle", items: [
        HelpItem(title: "Tapping a channel", blocks: [
            .text("Tap any channel card or row to start playing immediately. The player opens full-window with a sidebar showing all currently live channels."),
            .text("You can switch channels from the sidebar without leaving the player — just tap another row.")
        ]),
        HelpItem(title: "Fullscreen mode", blocks: [
            .text("Click the expand icon (bottom-right of the player controls) to hide the sidebar and go fullscreen. Click again to bring the sidebar back."),
            .text("You can also use keyboard shortcuts: Space to play/pause, M to mute, P for Picture in Picture, F for macOS fullscreen.")
        ]),
        HelpItem(title: "Picture in Picture", blocks: [
            .text("Click the PiP button in the player controls to float the video in a small overlay window. You can continue browsing channels while the stream plays."),
            .tip("PiP works even when you navigate back to the channel browser or schedule.")
        ]),
        HelpItem(title: "Mini player", blocks: [
            .text("When you navigate away from the player, a mini player widget appears in the bottom-right corner of every screen. Tap it to return to the full player, or close it to stop the stream.")
        ])
    ]),
    HelpSection(title: "Favourites", icon: "star", items: [
        HelpItem(title: "Starring channels", blocks: [
            .text("Tap the star icon on any channel card to add it to Favourites. Starred channels appear in the Favourites screen, sorted by live content first."),
            .text("The Favourites screen shows two sections: Live now (your starred channels currently broadcasting) and Up next (your starred channels with something starting soon).")
        ])
    ]),
    HelpSection(title: "Schedule", icon: "calendar", items: [
        HelpItem(title: "Browsing the schedule", blocks: [
            .text("The Schedule screen shows a 7-day programme guide. Select a day in the left column, then browse events grouped into time brackets — Live now, Morning, This afternoon, Tonight."),
            .text("Filter by sport using the chips in the top bar. Each event row shows the teams, kick-off time, channel, and a bell button to set a reminder.")
        ]),
        HelpItem(title: "Reminders", blocks: [
            .text("Tap the bell icon on any upcoming event to mark it. Reminder notifications are coming in a future update — the bell state is saved for the session.")
        ])
    ]),
    HelpSection(title: "Settings", icon: "gearshape", items: [
        HelpItem(title: "Adding a playlist source", blocks: [
            .text("Go to Settings → Sources. Tap Add Source and paste your M3U URL. Give it a label and choose a refresh interval. NativeStream will fetch and update channels on that schedule automatically."),
            .warn("If your M3U URL changes, update it here. The old URL will continue being used until you change it.")
        ]),
        HelpItem(title: "Adding an EPG source", blocks: [
            .text("Go to Settings → Sources. The EPG / TV Guide section shows your current XMLTV source. Paste the URL in Settings → TV Guide if you need to change it."),
            .tip("A good free EPG source is https://iptv-org.github.io/epg/ — it covers most international channels.")
        ]),
        HelpItem(title: "Buffer preset", blocks: [
            .text("Settings → Playback → Buffer Preset controls the trade-off between stream latency and stability. Low latency = less buffering delay but more likely to stall on poor connections. High stability = more delay but smoother playback.")
        ]),
        HelpItem(title: "Server status", blocks: [
            .text("The bottom of the Settings sidebar shows whether the StreamServer background process is reachable. If it shows 'Server unreachable', the Go server is not running."),
            .code("make run-server")
        ])
    ])
]

// MARK: - Developer reference content

private let developerSections: [HelpSection] = [
    HelpSection(title: "Architecture", icon: "cpu", items: [
        HelpItem(title: "Two-component system", blocks: [
            .text("NativeStream has two moving parts. The Mac app (SwiftUI) is the live TV client. StreamServer (Go binary) runs in the background, validates streams, and serves a live playlist."),
            .text("The Mac app never knows where stream links come from. It polls http://localhost:8888/playlist.m3u and plays whatever is there. All link discovery and health logic lives in the server."),
            .tip("This separation means you can update the server without touching the app, and vice versa.")
        ]),
        HelpItem(title: "App layer model", blocks: [
            .text("The app has two data layers. The channel layer (M3U playlist) gives you the list of channels and their stream URLs. The EPG layer (XMLTV) tells you what each channel is broadcasting right now and next."),
            .text("Every screen is EPG-driven. Sport filtering checks EPG programme titles, not channel group tags. A channel appears under Football only when its current EPG programme contains football keywords.")
        ])
    ]),
    HelpSection(title: "Design system", icon: "paintpalette", items: [
        HelpItem(title: "NS namespace", blocks: [
            .text("All design tokens live in DesignSystem.swift under the NS enum. Never hardcode hex values, font sizes, spacing, or radii in views."),
            .code("NS.bg          // #060810\nNS.surface2    // #131826\nNS.accent      // #0ea5e9\nNS.live        // #ef4444\nNS.Spacing.xl  // 20pt\nNS.Radius.lg   // 10pt"),
            .tip("Add new tokens to DesignSystem.swift first, then reference them. This keeps the design system as the single source of truth.")
        ]),
        HelpItem(title: "Typography", blocks: [
            .text("Three font families: Syne (display and labels), InstrumentSans (body and captions), DMMono (monospace and badges)."),
            .code("NS.Font.heading     // Syne Bold 16\nNS.Font.captionMed  // InstrumentSans-Medium 11\nNS.Font.monoSm      // DMMono-Regular 10"),
            .warn("Always use NS.Font.* tokens. Never use .system() for UI text — it breaks the visual consistency.")
        ]),
        HelpItem(title: "Component library", blocks: [
            .text("Atomic components live in NSComponents.swift. Use these before building custom UI."),
            .code("NSLiveBadge(isLive: true)        // pulsing red LIVE badge\nNSProgressBar(value: 0.6)        // progress bar\nNSToggle(isOn: $binding)         // capsule toggle\nNSChip(label:, isActive:)        // filter chip\nNSGroupHeader(title:, count:)    // section header\nChannelLogoSquare(channel:)      // 36pt square logo for rows\nChannelLogoView(channel:)        // 16:9 card logo")
        ])
    ]),
    HelpSection(title: "View hierarchy", icon: "square.3.layers.3d", items: [
        HelpItem(title: "Screen map", blocks: [
            .text("AppShell is the root. It owns the SportNavRail and routes AppDestination values to the correct screen."),
            .code("AppShell\n├── SportNavRail (persistent)\n├── NowScreen           (.now)\n├── MatchDayScreen      (.sport(SportCategory))\n├── FavouritesScreen    (.favourites)\n├── ScheduleScreen      (.schedule)\n├── BrowserScreen       (.allChannels)\n├── PlayerScreen        (modal)\n│   ├── PlayerOnNowTab\n│   └── PlayerScheduleTab\n├── HelpScreen          (.help)\n└── SettingsScreen      (sheet)")
        ]),
        HelpItem(title: "Environment objects", blocks: [
            .text("Five environment objects are injected at AppShell and available across all screens."),
            .code("PlaylistViewModel   // channels, sources, isLoading\nEPGViewModel        // currentProgramme, nextProgramme, schedule\nPlayerViewModel     // currentChannel, play, stop, PiP\nFavouritesManager   // toggle, isFavourite, favourites(from:)\nServerHealthViewModel // isConnected, status, startPolling")
        ])
    ]),
    HelpSection(title: "Data flow", icon: "arrow.triangle.2.circlepath", items: [
        HelpItem(title: "Startup sequence", blocks: [
            .text("On launch, AppShell.task fires loadAll() which runs playlist and EPG loading in parallel using async let. Server health polling starts after both complete."),
            .code("async let playlist = playlistVM.loadAll()\nasync let epg      = loadEPG()\n_ = await (playlist, epg)\nserverHealth.startPolling(serverURL: url)"),
            .text("EPG reloads automatically when the EPG URL changes in Settings via .onChange(of: settings.epgURLString).")
        ]),
        HelpItem(title: "EPG lookups", blocks: [
            .text("EPGViewModel exposes three primary query methods used throughout the app."),
            .code("epgVM.currentProgramme(for: channel)  // what's on now\nepgVM.nextProgramme(for: channel)     // what's on next\nepgVM.schedule(for: channel, hours:)  // upcoming window"),
            .text("Sport filtering uses activeSports(in:) which checks currentProgramme and nextProgramme across all channels and sorts by live count descending.")
        ]),
        HelpItem(title: "Adding a new screen", blocks: [
            .text("1. Add a case to AppDestination in SportNavRail.swift."),
            .text("2. Add a RailIcon to SportNavRail."),
            .text("3. Add a case to the switch in AppShell.destinationContent."),
            .text("4. Create the screen file under Views/YourFeature/YourScreen.swift."),
            .tip("Follow the existing pattern: VStack with topBar + Divider + content. Inject environments you need, compute derived data as private vars.")
        ])
    ]),
    HelpSection(title: "StreamServer", icon: "server.rack", items: [
        HelpItem(title: "Running the server", blocks: [
            .text("The Go server runs as a background binary. It serves the playlist at /playlist.m3u and EPG at /epg.xml, validates stream health, and replaces dead links automatically."),
            .code("make run-server          # run in foreground\nmake install-service     # install as launchd service"),
            .text("The app polls http://localhost:8888 by default. Change the URL in Settings → Server if running remotely.")
        ]),
        HelpItem(title: "Health API", blocks: [
            .text("ServerHealthViewModel polls /api/health every 30 seconds. The response drives the status indicator in the Settings sidebar."),
            .code("GET /api/health\n→ { \"channels\": 142, \"healthy\": 138 }"),
            .warn("If the server is unreachable, the app still works — it falls back to whatever playlist was last loaded. No crash, no empty state.")
        ])
    ]),
    HelpSection(title: "Contributing", icon: "chevron.left.forwardslash.chevron.right", items: [
        HelpItem(title: "Ticket system", blocks: [
            .text("Work is tracked in NativeStream_UX_Tickets.md. Each ticket has an ID (UX-001), effort estimate, Needs (files to provide as context), and a Done when acceptance criterion."),
            .text("When starting a ticket, paste only the files listed in Needs. Context from prior tickets carries forward — don't re-paste files already provided.")
        ]),
        HelpItem(title: "Code conventions", blocks: [
            .text("All design tokens from NS namespace. All atomic UI from NSComponents. No hardcoded hex, spacing, or font sizes in views. Borders always lineWidth: 0.5. Card grids always use the background GeometryReader pattern with NS.CardSize.minWidth."),
            .code("// Grid pattern\n@State private var gridWidth: CGFloat = 0\nlet columns = max(1, Int(gridWidth / NS.CardSize.minWidth))\nlet grid = Array(repeating: GridItem(.flexible(),\n    spacing: NS.Spacing.sm), count: columns)")
        ])
    ])
]
