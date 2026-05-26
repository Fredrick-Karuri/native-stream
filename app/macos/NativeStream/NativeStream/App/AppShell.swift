// AppShell.swift — FX-003, FX-006, FX-007
import SwiftUI
import AVKit

struct AppShell: View {

    @Environment(PlaylistViewModel.self)     private var playlistVM
    @Environment(EPGViewModel.self)          private var epgVM
    @Environment(PlayerViewModel.self)       private var playerVM
    @Environment(SettingsStore.self)         private var settings
    @Environment(FavouritesManager.self)     private var favourites
    @Environment(ServerHealthViewModel.self) private var serverHealth
    // ChannelManagerViewModel is in the environment for child screens.
    // AppShell does not use it directly — no @Environment declaration needed here.

    @State private var destination: AppDestination = .now
    @State private var selectedChannel: Channel?   = nil
    @State private var showPlayer                  = false
    @State private var keyMonitor: Any?            = nil

    var body: some View {
        HStack(spacing: 0) {
            if !showPlayer {
                SportNavRail(destination: $destination)
            }

            ZStack(alignment: .bottomTrailing) {
                destinationContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                if playerVM.currentChannel != nil && !showPlayer {
                    MiniPlayerWidget(onExpand: { showPlayer = true }, onClose: { playerVM.stop() })
                        .padding(NS.Spacing.lg)
                        .transition(.asymmetric(
                            insertion: .move(edge: .bottom).combined(with: .opacity),
                            removal:   .move(edge: .bottom).combined(with: .opacity)
                        ))
                }
            }
        }
        .background(NS.bg)
        .frame(minWidth: 960, minHeight: 580)
        .task { await loadAll() }                                          // FX-003: single load site
        .onChange(of: settings.epgURLString) { Task { await loadEPG() } }
        .onChange(of: showPlayer) { _, isShowing in
            if isShowing {
                keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
                    switch event.charactersIgnoringModifiers {
                    case " ": playerVM.togglePlayback()
                    case "m", "M": playerVM.toggleMute()
                    case "p", "P": playerVM.enterPiP()
                    case "f", "F": NSApp.mainWindow?.toggleFullScreen(nil)
                    default: break
                    }
                    return event
                }
            } else {
                if let m = keyMonitor { NSEvent.removeMonitor(m); keyMonitor = nil }
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: playerVM.isPlaying)
    }

    // MARK: - Load

    private func loadAll() async {
        async let playlist: () = playlistVM.loadAll()
        async let epg: ()      = loadEPG()
        _ = await (playlist, epg)
        
        if settings.epgURLString.isEmpty, let detected = playlistVM.detectedEPGURL {
            settings.epgURLString = detected.absoluteString
            await loadEPG()
        }
        if let url = settings.serverURL { serverHealth.startPolling(serverURL: url) }
        playlistVM.scheduleAutoRefresh()
    }

    private func loadEPG() async {
        epgVM.epgURL = settings.epgURL   // FX-005: wire Settings URL
        await epgVM.load()
    }

    // MARK: - Routing

    @ViewBuilder
    private var destinationContent: some View {
        if showPlayer {
            PlayerScreen(channel: selectedChannel, onBack: {
                playerVM.pipController?.stopPictureInPicture()
                playerVM.pipController = nil
                playerVM.pipActive     = false
                showPlayer             = false
            })
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal:   .move(edge: .leading).combined(with: .opacity)
            ))
        } else {
            Group {
                switch destination {
                case .now:          NowScreen(onSelectChannel: selectChannel)
                case .sport(let s): MatchDayScreen(sport: s, onSelectChannel: selectChannel)
                case .favourites:   FavouritesScreen(onSelectChannel: selectChannel)
                case .schedule:     ScheduleScreen(onSelectChannel: selectChannel)
                case .allChannels:  BrowserScreen(onSelectChannel: selectChannel)
                case .help:         HelpScreen()
                case .settings:     SettingsScreen()
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal:   .move(edge: .leading).combined(with: .opacity)
            ))
        }
    }

    // MARK: - Channel selection (FX-006: explicit error handling)

    private func selectChannel(_ channel: Channel) {
        selectedChannel       = channel
        playerVM.bufferPreset = settings.bufferPreset
        playerVM.epgViewModel = epgVM
        Task {
            do {
                try await playerVM.play(channel: channel)
            } catch {
                // play() already falls back to channel.streamURL.
                // If that also fails, playerVM.error is set and
                // PlayerScreen shows the error overlay automatically.
                print("⚠️ [AppShell] Play failed for \(channel.name): \(error.localizedDescription)")
            }
        }
        withAnimation(.easeInOut(duration: 0.25)) { showPlayer = true }
    }
}

#Preview {
    AppShell()
        .environment(PlaylistViewModel())
        .environment(EPGViewModel())
        .environment(PlayerViewModel())
        .environment(SettingsStore())
        .environment(FavouritesManager())
        .environment(ServerHealthViewModel())
        .environment(ChannelManagerViewModel())
}
