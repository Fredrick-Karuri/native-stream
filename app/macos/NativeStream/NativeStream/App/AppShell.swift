// AppShell.swift — FX-003, FX-006, FX-007, FX-018
import SwiftUI
import AVKit

struct AppShell: View {

    @Environment(PlaylistViewModel.self)     private var playlistVM
    @Environment(EPGViewModel.self)          private var epgVM
    @Environment(PlayerViewModel.self)       private var playerVM
    @Environment(SettingsStore.self)         private var settings
    @Environment(FavouritesManager.self)     private var favourites
    @Environment(ServerHealthViewModel.self) private var serverHealth

    @State private var destination: AppDestination = .now
    @State private var selectedChannel: Channel?   = nil
    @State private var showPlayer                  = false
    @State private var showPlayURL                 = false   // FX-018
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
        .task { await loadAll() }
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
        // FX-018: ⌘U opens Play URL sheet
        .sheet(isPresented: $showPlayURL) {
            PlayURLSheet(isPresented: $showPlayURL) {
                withAnimation(.easeInOut(duration: 0.25)) { showPlayer = true }
            }
            .environment(playerVM)
        }
        .keyboardShortcut("u", modifiers: .command)
        .onAppear {
            NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
                if event.modifierFlags.contains(.command),
                   event.charactersIgnoringModifiers == "u" {
                    showPlayURL = true
                    return nil
                }
                return event
            }
        }
    }

    // MARK: - Load

    private func loadAll() async {
        async let playlist: () = playlistVM.loadAll()
        async let epg: ()      = loadEPG()
        _ = await (playlist, epg)
        if let url = settings.serverURL { serverHealth.startPolling(serverURL: url) }
        playlistVM.scheduleAutoRefresh()
        epgVM.logMatchDiagnostic(for: playlistVM.channels)
    }

    private func loadEPG() async {
        epgVM.epgURL = settings.epgURL
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

    // MARK: - Channel selection

    private func selectChannel(_ channel: Channel) {
        selectedChannel       = channel
        playerVM.bufferPreset = settings.bufferPreset
        playerVM.epgViewModel = epgVM
        Task {
            do {
                try await playerVM.play(channel: channel)
            } catch {
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
