// AppShell.swift — UX-002
// Root window: custom tab bar + tab content switching.
// Replaces NavigationSplitView entirely.

import SwiftUI
import AVKit

enum AppTab: String, CaseIterable {
    case browse   = "browse"
    case matchDay = "matchDay"
    case tvGuide  = "tvGuide"

    var label: String {
        switch self {
        case .browse:   return "⊞  Browse"
        case .matchDay: return "⚽  Match Day"
        case .tvGuide:  return "📺  TV Guide"
        }
    }
}

struct AppShell: View {

    @Environment(PlaylistViewModel.self)     private var playlistVM
    @Environment(EPGViewModel.self)          private var epgVM
    @Environment(PlayerViewModel.self)       private var playerVM
    @Environment(SettingsStore.self)         private var settings
    @Environment(FavouritesManager.self)     private var favourites
    @Environment(ServerHealthViewModel.self) private var serverHealth

    @State var activeTab: AppTab = .browse
    @State var selectedChannel: Channel? = nil
    @State var showPlayer = false

    var body: some View {
        VStack(spacing: 0) {
            if !showPlayer{
            AppTabBar(activeTab: $activeTab, onRefresh: { Task { await playlistVM.loadAll() } })
            Divider().overlay(NS.border)
            }

            ZStack(alignment: .bottomTrailing) {
                tabContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Floating mini player
                if playerVM.currentChannel != nil && !showPlayer {
                    MiniPlayerWidget(onExpand: { showPlayer = true }, onClose: { playerVM.stop() })
                        .padding(16)
                        .transition(.asymmetric(
                            insertion: .move(edge: .bottom).combined(with: .opacity),
                            removal:   .move(edge: .bottom).combined(with: .opacity)
                        ))
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.85), value: playerVM.isPlaying)
        }
        .background(NS.bg)
        .frame(minWidth: 960, minHeight: 580)
    }

    @ViewBuilder
    private var tabContent: some View {
        if showPlayer {
            
            PlayerScreen(channel: selectedChannel, onBack: {
                playerVM.pipController?.stopPictureInPicture()
                playerVM.pipController = nil
                playerVM.pipActive = false
                showPlayer = false
            })
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal:   .move(edge: .leading).combined(with: .opacity)
                ))
        } else {
            Group {
                switch activeTab {
                case .browse:
                    BrowserScreen(onSelectChannel: selectChannel)
                case .matchDay:
                    MatchDayScreen(onSelectChannel: selectChannel)
                case .tvGuide:
                    EPGGridScreen(onSelectChannel: selectChannel)
                }
            }
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal:   .move(edge: .leading).combined(with: .opacity)
            ))
        }
    }

    private func selectChannel(_ channel: Channel) {
        selectedChannel = channel
        playerVM.bufferPreset = settings.bufferPreset
        playerVM.epgViewModel = epgVM
        playerVM.play(channel: channel)
        withAnimation(.easeInOut(duration: 0.25)) { showPlayer = true }
    }
}

// MARK: - Tab Bar

struct AppTabBar: View {
    @Binding var activeTab: AppTab
    let onRefresh: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Spacer()
            HStack(spacing: 2) {
                ForEach(AppTab.allCases, id: \.self) { tab in
                    TabSegment(tab: tab, isActive: activeTab == tab) {
                        withAnimation(.easeInOut(duration: 0.22)) { activeTab = tab }
                    }
                }
            }
            Spacer()
            HStack(spacing: 6) {

                Button(action: onRefresh) {
                    TBButtonLabel(label: "↻")
                }
                .buttonStyle(.plain)

                SettingsLink {
                    TBButtonLabel(label: "⚙")
                }
                .buttonStyle(.plain)

            }
            .padding(.trailing, 16)
        }
        .frame(height: 44)
        .background(NS.surface)
    }
}

struct TabSegment: View {
    let tab: AppTab
    let isActive: Bool
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Text(tab.label)
                .font(NS.Font.captionMed)
                .foregroundStyle(isActive ? NS.accent2 : (isHovered ? NS.text2 : NS.text3))
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(
                    Group {
                        if isActive { NS.accentGlow }
                        else if isHovered { NS.surface2 }
                        else { Color.clear }
                    }
                )
                .clipShape(RoundedRectangle(cornerRadius: 7))
                .overlay(
                    RoundedRectangle(cornerRadius: 7)
                        .stroke(isActive ? NS.accentBorder : Color.clear)
                )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

struct TBButtonLabel: View {
    let label: String
    @State private var isHovered = false

    var body: some View {
        Text(label)
            .font(NS.Font.caption)
            .foregroundStyle(isHovered ? NS.text : NS.text2)
            .padding(.horizontal, 10)
            .frame(height: 26)
            .background(isHovered ? NS.surface3 : NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(NS.border2))
            .onHover { isHovered = $0 }
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
}
