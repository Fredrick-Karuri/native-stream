//
//  UserGuide.swift
//  NativeStream
//


// MARK: - User guide content

let userGuideSections: [HelpSection] = [
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

