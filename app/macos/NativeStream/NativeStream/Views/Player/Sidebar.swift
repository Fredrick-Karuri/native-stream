// Sidebar.swift

import SwiftUI


// MARK: - Player Sidebar

struct PlayerSidebar: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(PlayerViewModel.self)   private var playerVM

    let currentChannel: Channel?

    enum SidebarTab { case onNow, schedule }
    @State private var tab: SidebarTab = .onNow

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                sidebarTab("On now",   tab: .onNow)
                sidebarTab("Schedule", tab: .schedule)
            }
            .background(Color.black.opacity(0.6))
            .overlay(alignment: .bottom) {
                Rectangle().fill(Color.white.opacity(0.07)).frame(height: 0.5)
            }

            switch tab {
            case .onNow:    PlayerOnNowTab(currentChannel: currentChannel)
            case .schedule: PlayerScheduleTab(channel: playerVM.currentChannel)
            }
        }
        .frame(width: NS.Player.sidebarWidth)
        .background(Color(hex: "0e0e0e"))
        .overlay(alignment: .leading) {
            Rectangle().fill(Color.white.opacity(0.07)).frame(width: 0.5)
        }
    }

    private func sidebarTab(_ label: String, tab: SidebarTab) -> some View {
        Button { self.tab = tab } label: {
            Text(label)
                .font(NS.Font.captionMed)
                .foregroundStyle(self.tab == tab ? Color.white.opacity(0.85) : Color.white.opacity(0.35))
                .frame(maxWidth: .infinity)
                .padding(.vertical, NS.Spacing.sm)
                .overlay(alignment: .bottom) {
                    if self.tab == tab {
                        Rectangle().fill(NS.accent).frame(height: 1.5)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}


// MARK: - On Now Tab

struct PlayerOnNowTab: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(PlayerViewModel.self)   private var playerVM

    let currentChannel: Channel?

    // Only channels with live or upcoming EPG content.
    // Falls back to all channels if EPG has no data at all.

    private var filteredChannels: [Channel] {
        let all = playlistVM.channels
        let withEPG = all.filter {
            epgVM.currentProgramme(for: $0) != nil || epgVM.nextProgramme(for: $0) != nil
        }
        return withEPG.isEmpty ? all : withEPG
    }

    private var sortedChannels: [Channel] {
        filteredChannels.sorted { a, b in
            let aLive = epgVM.currentProgramme(for: a) != nil
            let bLive = epgVM.currentProgramme(for: b) != nil
            if aLive != bLive { return aLive }
            return epgVM.nextProgramme(for: a) != nil && epgVM.nextProgramme(for: b) == nil
        }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(sortedChannels) { channel in
                        PlayerSidebarRow(channel: channel)
                            .id(channel.id)
                    }
                }
                .padding(NS.Spacing.sm)
            }
            .onAppear {
                if playerVM.channelList.isEmpty {
                    playerVM.channelList = sortedChannels
                }
                if let current = playerVM.currentChannel {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        withAnimation { proxy.scrollTo(current.id, anchor: .center) }
                    }
                }
            }
            .onChange(of: playerVM.currentChannel?.id) { _, id in
                guard let id else { return }
                withAnimation { proxy.scrollTo(id, anchor: .center) }
            }

        }
    }
}

// MARK: - Schedule Tab

struct PlayerScheduleTab: View {

    @Environment(EPGViewModel.self)    private var epgVM
    @Environment(PlayerViewModel.self) private var playerVM

    let channel: Channel?

    private var programmes: [Programme] {
        guard let ch = channel else { return [] }
        return epgVM.schedule(for: ch, hours: 12).sorted { $0.start < $1.start }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                ForEach(programmes, id: \.id) { prog in
                    
                    scheduleRow(prog)
                }
            }
            .padding(NS.Spacing.sm)
        }
    }

    private func scheduleRow(_ prog: Programme) -> some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xs) {
            Text(prog.startTimeString)
                .font(NS.Font.monoSm)
                .foregroundStyle(prog.isNow ? NS.accent : Color.white.opacity(0.3))
            if prog.isNow {
                Text("Now playing")
                    .font(.system(size: NS.Schedule.microLabelSize, weight: .bold))
                    .foregroundStyle(NS.accent)
                    .kerning(0.5)
            }
            Text(prog.title)
                .font(NS.Font.captionMed)
                .foregroundStyle(prog.isNow ? Color.white.opacity(0.85) : Color.white.opacity(0.4))
                .lineLimit(2)
            if prog.isNow {
                NSProgressBar(value: prog.progress, height: 2, glow: false).opacity(0.6)
            }
        }
        .padding(NS.Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(prog.isNow ? NS.accentGlow : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.md)
                .stroke(prog.isNow ? NS.accentBorder : Color.clear, lineWidth: 0.5)
        )
        .opacity(prog.stop < Date() ? 0.4 : 1.0)
    }
}


struct PlayerSidebarRow: View {

    @Environment(EPGViewModel.self)    private var epgVM
    @Environment(PlayerViewModel.self) private var playerVM

    let channel: Channel

    private var isPlaying: Bool { playerVM.currentChannel?.id == channel.id }
    private var current: Programme? { epgVM.currentProgramme(for: channel) }
    private var next: Programme?    { epgVM.nextProgramme(for: channel) }
    private var isLive: Bool        { current != nil }

    var body: some View {
        Button {
            Task { try? await playerVM.play(channel: channel) }
        }
        label: {
            HStack(spacing: NS.Spacing.sm) {

                ChannelLogoSquare(
                    channel: channel,
                    size: NS.Channel.logoSquareSm,
                    cornerRadius: NS.Radius.sm
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.name)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(Color.white.opacity(isPlaying ? 0.9 : 0.6))
                        .lineLimit(1)

                    if let prog = current ?? next {
                        Text(prog.title)
                            .font(NS.Font.monoSm)
                            .foregroundStyle(Color.white.opacity(0.3))
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Right indicator
                if isPlaying {
                    Image(systemName: "play.fill")
                        .font(.system(size: 9))
                        .foregroundStyle(NS.accent)
                } else if let prog = current {
                    Text(prog.timeRemainingString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(Color.white.opacity(0.3))
                
                } else if let next {
                    Text(next.startTimeString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.accent.opacity(0.7))
                }
            }
            .padding(.horizontal, NS.Spacing.sm)
            .padding(.vertical, NS.Spacing.xs)
            .background(isPlaying ? NS.accentGlow : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        }
        .buttonStyle(.plain)
    }
}
