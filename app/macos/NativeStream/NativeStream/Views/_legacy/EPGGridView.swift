// EPGGridView.swift — NS-310
// Full EPG grid: horizontal time axis, vertical channel axis, programme cells.
// Click a cell to switch to that channel.

import SwiftUI

struct EPGGridView: View {

    @Environment(EPGViewModel.self)      private var epgVM
    @Environment(PlaylistViewModel.self) private var playlistVM

    var onSelectChannel: (Channel) -> Void

    // Time window: now-30min to now+6h
    private let hoursVisible: Double = 6.5
    private let channelColumnWidth: CGFloat = 160
    private let hourWidth: CGFloat = 180
    private let rowHeight: CGFloat = 52

    @State private var scrollOffset: CGFloat = 0

    private var windowStart: Date {
        Calendar.current.date(byAdding: .minute, value: -30, to: Date()) ?? Date()
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header row: channel label column + time axis
            HStack(spacing: 0) {
                Color.clear
                    .frame(width: channelColumnWidth, height: 32)
                    .background(.background.secondary)
                TimelineHeaderView(
                    windowStart: windowStart,
                    hoursVisible: hoursVisible,
                    hourWidth: hourWidth
                )
            }
            .frame(height: 32)

            Divider()

            // Scrollable body
            ScrollView([.horizontal, .vertical]) {
                LazyVStack(spacing: 0, pinnedViews: .sectionHeaders) {
                    ForEach(playlistVM.channels) { channel in
                        HStack(spacing: 0) {
                            // Pinned channel name column
                            ChannelLabelCell(channel: channel)
                                .frame(width: channelColumnWidth, height: rowHeight)

                            // Programme cells
                            ZStack(alignment: .leading) {
                                // Background
                                Rectangle()
                                    .fill(Color.primary.opacity(0.03))
                                    .frame(width: totalWidth, height: rowHeight)

                                // Now marker
                                nowMarker

                                // Programmes
                                ForEach(epgVM.schedule(for: channel, hours: Int(hoursVisible)), id: \.title) { prog in
                                    ProgrammeCellView(
                                        programme: prog,
                                        windowStart: windowStart,
                                        hourWidth: hourWidth
                                    ) {
                                        onSelectChannel(channel)
                                    }
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
        .background(.background)
    }

    private var totalWidth: CGFloat {
        CGFloat(hoursVisible) * hourWidth
    }

    private var nowMarker: some View {
        let offset = CGFloat(Date().timeIntervalSince(windowStart) / 3600) * hourWidth
        return Rectangle()
            .fill(.red.opacity(0.6))
            .frame(width: 2, height: rowHeight)
            .offset(x: offset)
    }
}

// MARK: - Timeline header

struct TimelineHeaderView: View {
    let windowStart: Date
    let hoursVisible: Double
    let hourWidth: CGFloat

    var body: some View {
        ZStack(alignment: .leading) {
            ForEach(0..<Int(hoursVisible) + 1, id: \.self) { i in
                let time = windowStart.addingTimeInterval(TimeInterval(i) * 3600)
                Text(timeString(time))
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .offset(x: CGFloat(i) * hourWidth + 4)
            }
        }
        .frame(width: CGFloat(hoursVisible) * hourWidth, alignment: .leading)
        .background(.background.secondary)
    }

    private func timeString(_ date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: date)
    }
}

// MARK: - Channel label cell

struct ChannelLabelCell: View {
    @Environment(EPGViewModel.self) private var epgVM
    let channel: Channel

    var body: some View {
        HStack(spacing: 8) {
            AsyncImage(url: channel.logoURL) { phase in
                if case .success(let img) = phase {
                    img.resizable().scaledToFit()
                } else {
                    Image(systemName: "tv").foregroundStyle(.secondary)
                }
            }
            .frame(width: 24, height: 24)
            .clipShape(RoundedRectangle(cornerRadius: 4))

            Text(channel.name)
                .font(.system(size: 11, weight: .medium))
                .lineLimit(2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .background(.background.secondary)
    }
}

// MARK: - Programme cell

struct ProgrammeCellView: View {
    let programme: Programme
    let windowStart: Date
    let hourWidth: CGFloat
    let onTap: () -> Void

    private var xOffset: CGFloat {
        let secs = programme.start.timeIntervalSince(windowStart)
        return max(0, CGFloat(secs / 3600) * hourWidth)
    }

    private var width: CGFloat {
        let dur = programme.stop.timeIntervalSince(programme.start)
        return max(2, CGFloat(dur / 3600) * hourWidth - 2)
    }

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .leading) {
                // Progress fill
                GeometryReader { geo in
                    Rectangle()
                        .fill(programme.isNow ? Color.accentColor.opacity(0.25) : Color.primary.opacity(0.06))
                    if programme.isNow {
                        Rectangle()
                            .fill(Color.accentColor.opacity(0.15))
                            .frame(width: geo.size.width * programme.progress)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 4))

                Text(programme.title)
                    .font(.system(size: 11))
                    .lineLimit(2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
            }
            .frame(width: width, height: 44)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(programme.isNow ? Color.accentColor.opacity(0.5) : Color.primary.opacity(0.1),
                            lineWidth: programme.isNow ? 1.5 : 0.5)
            )
        }
        .buttonStyle(.plain)
        .offset(x: xOffset)
    }
}