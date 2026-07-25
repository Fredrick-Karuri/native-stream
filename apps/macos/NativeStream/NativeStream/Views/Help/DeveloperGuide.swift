//
//  DeveloperGuide.swift
//  NativeStream
//


// MARK: - Developer reference content

let developerSections: [HelpSection] = [
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


