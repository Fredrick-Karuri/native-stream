// File: AppShell.swift
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
    @State private var browserVM = BrowserViewModel()
    @State private var selectedChannel: Channel?   = nil
    @State private var showPlayer                  = false
    @State private var showPlayURL                 = false
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
        // Hidden Button triggers the Cmd + F action anywhere globally
        .background {
            Button("") {
                destination = .allChannels
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    NotificationCenter.default.post(name: .focusSearchField, object: nil)
                }
            }
            .keyboardShortcut("f", modifiers: .command)
            .opacity(0)
            .allowsHitTesting(false)
        }
        .task { await loadAll() }
        .onChange(of: settings.epgURLString) { Task { await loadEPG() } }
        // Dynamic Key Monitoring for Player Overrides and Esc intercepts
        .onChange(of: destination) { _, _ in updateKeyMonitor() }
        .onChange(of: showPlayer)   { _, _ in updateKeyMonitor() }
        .task { updateKeyMonitor() } // Set up on initial launch
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: playerVM.isPlaying)
        .sheet(isPresented: $showPlayURL) {
            PlayURLSheet(isPresented: $showPlayURL) {
                withAnimation(.easeInOut(duration: 0.25)) { showPlayer = true }
            }
            .environment(playerVM)
        }
        .onReceive(NotificationCenter.default.publisher(for: .showHelp)) { _ in
            destination = .help
        }
        .onReceive(NotificationCenter.default.publisher(for: .openPlayURL)) { _ in
            showPlayURL = true
        }
    }

    // MARK: - Helpers
    
    private func closePlayerView() {
        playerVM.pipController?.stopPictureInPicture()
        playerVM.pipController = nil
        playerVM.pipActive     = false
        withAnimation { showPlayer = false }
    }

    // MARK: - Load
    private func loadAll() async {
        await playlistVM.loadAll()
        if let url = settings.serverURL { serverHealth.startPolling(serverURL: url) }
        playlistVM.scheduleAutoRefresh()
        Task(priority: .background) {
            await loadEPG()
            epgVM.logMatchDiagnostic(for: playlistVM.channels)
        }
    }

    private func loadEPG() async {
        epgVM.epgURL = settings.epgURL
        await epgVM.load(sources: playlistVM.sources)
    }

    // MARK: - Routing
    @ViewBuilder
    private var destinationContent: some View {
        if showPlayer {
            PlayerScreen(channel: selectedChannel, onBack: { closePlayerView() })
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
                                                        .environment(browserVM)
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
    
    // MARK: - Keyboard Monitor Engine

    // MARK: - Keyboard Monitor Engine

    private func updateKeyMonitor() {
        if let m = keyMonitor { NSEvent.removeMonitor(m); keyMonitor = nil }
        
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            // Catch Escape Key (Virtual Key Code: 53)
            if event.keyCode == 53 {
                
                // Fix: If a text box is actively focused, let SwiftUI's .onExitCommand handle it instead!
                if let firstResponder = NSApp.mainWindow?.firstResponder,
                   let className = Optional(String(describing: type(of: firstResponder))),
                   className.contains("NSText") || className.contains("Field") {
                    return event // Pass through normally; do not change destination!
                }
                
                // Tier 2: Player escape handling
                if showPlayer {
                    closePlayerView()
                    return nil
                }
                
                // Tier 3: Global navigation jump home
                if destination != .now && !showPlayURL {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        destination = .now
                    }
                    return nil
                }
            }
            
            // Default player hotkeys
            if showPlayer {
                switch event.charactersIgnoringModifiers {
                case " ": playerVM.togglePlayback(); return nil
                case "m", "M": playerVM.toggleMute(); return nil
                case "p", "P": playerVM.enterPiP(); return nil
                case "f", "F": NSApp.mainWindow?.toggleFullScreen(nil); return nil
                default: break
                }
            }
            
            return event
        }
    }


}

extension Notification.Name {
    static let showHelp = Notification.Name("showHelp")
    static let openPlayURL = Notification.Name("openPlayURL")
    static let focusSearchField = Notification.Name("focusSearchField") // New communication pipe
}


