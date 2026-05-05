// EPGGridScreen.swift — UX-050, UX-051
// Screen 5: EPG grid. Sticky channel column, horizontal time scroll, now-line.

import SwiftUI
import Combine

struct EPGGridScreen: View {

    @Environment(EPGViewModel.self)        private var epgVM
    @Environment(PlaylistViewModel.self)   private var playlistVM

    let onSelectChannel: (Channel) -> Void

    // 30-min slot = 150pt
    private let slotWidth:     CGFloat = 150
    private let slotMins:      Double  = 30
    private let rowHeight:     CGFloat = 60
    private let cellHeight:    CGFloat = 52
    private let channelColW:   CGFloat = 172
    private let timelineH:     CGFloat = 36

    // Show from now-30min, 6 hours forward
    private var windowStart: Date {
        Calendar.current.date(byAdding: .minute, value: -30, to: Date()) ?? Date()
    }
    private var totalSlots: Int { 13 } // 6.5 hours

    // Shared vertical scroll offset
    @State private var verticalOffset: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            epgHeader
            Divider().overlay(NS.border)

            HStack(spacing: 0) {
                // Pinned channel column
                VStack(spacing: 0) {
                    // Corner cell
                    Text("Channel")
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                        .frame(width: channelColW, height: timelineH, alignment: .leading)
                        .padding(.leading, 12)
                        .background(NS.surface2)
                        .border(NS.border, width: 1)

                    // Channel rows
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(spacing: 0) {
                            ForEach(playlistVM.channels.prefix(20)) { ch in
                                EPGChannelLabel(
                                    channel: ch,
                                    isActive: epgVM.currentProgramme(for: ch) != nil,
                                    height: rowHeight
                                )
                                Divider().overlay(NS.border)
                            }
                        }
                    }
                    .disabled(true) // synced via offset below — simplified for V1
                }
                .frame(width: channelColW)
                .border(NS.border, width: 1)

                // Scrollable grid
                ScrollView([.horizontal, .vertical]) {
                    ZStack(alignment: .topLeading) {
                        VStack(spacing: 0) {
                            // Timeline header
                            EPGTimelineHeader(
                                windowStart: windowStart,
                                slots: totalSlots,
                                slotWidth: slotWidth,
                                slotMins: slotMins,
                                height: timelineH
                            )
                            Divider().overlay(NS.border)

                            // Programme rows
                            ForEach(playlistVM.channels.prefix(20)) { ch in
                                EPGProgrammeRow(
                                    channel: ch,
                                    programmes: epgVM.schedule(for: ch, hours: 7),
                                    windowStart: windowStart,
                                    slotWidth: slotWidth,
                                    slotMins: slotMins,
                                    rowHeight: rowHeight,
                                    cellHeight: cellHeight,
                                    onSelect: { onSelectChannel(ch) }
                                )
                                Divider().overlay(NS.border)
                            }
                        }

                        // Now line
                        NowLine(
                            windowStart: windowStart,
                            slotWidth: slotWidth,
                            slotMins: slotMins,
                            rowHeight: rowHeight,
                            rowCount: min(20, playlistVM.channels.count),
                            timelineH: timelineH
                        )
                    }
                    .frame(minWidth: CGFloat(totalSlots) * slotWidth)
                }
            }
        }
        .background(NS.bg)
    }

    private var epgHeader: some View {
        HStack {
            Text("TV Guide")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            Spacer()

            Button {
            } label: {
                TBButtonLabel(label: "◀  \(dayString)  ▶")
            }
            .buttonStyle(.plain)

            Button {
            } label: {
                TBButtonLabel(label: "↻  Now")
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, NS.Spacing.xl)
        .frame(height: 52)
        .background(NS.surface)
    }

    private var dayString: String {
        let f = DateFormatter(); f.dateFormat = "EEE d MMM"
        return f.string(from: Date())
    }
}

// MARK: - Channel label

struct EPGChannelLabel: View {
    let channel: Channel
    let isActive: Bool
    let height: CGFloat

    var body: some View {
        HStack(spacing: 10) {
            ChannelLogoView(channel: channel)
                .frame(width: 30, height: 30)

            VStack(alignment: .leading, spacing: 2) {
                Text(channel.name)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(isActive ? NS.accent2 : NS.text)
                    .lineLimit(1)
                Text(isActive ? "● LIVE" : channel.groupTitle)
                    .font(.system(size: 9))
                    .foregroundStyle(isActive ? NS.accent2 : NS.text3)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .frame(height: height)
        .background(
            isActive
                ? LinearGradient(colors: [NS.accentGlow, .clear], startPoint: .leading, endPoint: .trailing)
                : LinearGradient(colors: [.clear, .clear], startPoint: .leading, endPoint: .trailing)
        )
        .overlay(alignment: .leading) {
            if isActive {
                Rectangle().fill(NS.accent).frame(width: 2)
            }
        }
        .background(NS.surface)
    }
}

// MARK: - Timeline header

struct EPGTimelineHeader: View {
    let windowStart: Date
    let slots: Int
    let slotWidth: CGFloat
    let slotMins: Double
    let height: CGFloat

    private var now: Date { Date() }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(0..<slots, id: \.self) { i in
                let slotTime = windowStart.addingTimeInterval(TimeInterval(i) * slotMins * 60)
                let isNowSlot = slotTime <= now && now < slotTime.addingTimeInterval(slotMins * 60)

                HStack {
                    Text(timeStr(slotTime))
                        .font(NS.Font.monoSm)
                        .foregroundStyle(isNowSlot ? NS.accent2 : NS.text3)
                        .fontWeight(isNowSlot ? .semibold : .regular)
                    if isNowSlot { Text("◆").font(.system(size: 8)).foregroundStyle(NS.accent2) }
                    Spacer()
                }
                .frame(width: slotWidth, height: height)
                .padding(.leading, 8)
                .background(NS.surface2)
                .border(NS.border, width: 0.5)
            }
        }
        .frame(height: height)
    }

    private func timeStr(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "HH:mm"; return f.string(from: d)
    }
}

// MARK: - Programme row

struct EPGProgrammeRow: View {
    let channel: Channel
    let programmes: [Programme]
    let windowStart: Date
    let slotWidth: CGFloat
    let slotMins: Double
    let rowHeight: CGFloat
    let cellHeight: CGFloat
    let onSelect: () -> Void

    private var totalWidth: CGFloat { slotWidth * 13 }
    private var pixelsPerSecond: CGFloat { slotWidth / CGFloat(slotMins * 60) }

    var body: some View {
        ZStack(alignment: .leading) {
            // Background
            Rectangle().fill(NS.surface2).frame(height: rowHeight)

            if programmes.isEmpty {
                // No EPG placeholder
                Text("No EPG data available")
                    .font(NS.Font.caption)
                    .foregroundStyle(NS.text3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: cellHeight)
                    .padding(.horizontal, 12)
                    .background(NS.surface2)
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.sm).stroke(NS.border2, style: StrokeStyle(dash: [4])))
                    .padding(.horizontal, 2)
                    .opacity(0.4)
            } else {
                ForEach(programmes, id: \.title) { prog in
                    EPGProgrammeCell(
                        programme: prog,
                        windowStart: windowStart,
                        pixelsPerSecond: pixelsPerSecond,
                        cellHeight: cellHeight,
                        onTap: onSelect
                    )
                }
            }
        }
        .frame(minWidth: totalWidth, maxWidth: .infinity)
        .frame(height: rowHeight)
        .clipped()
    }
}

// MARK: - Programme cell

struct EPGProgrammeCell: View {
    let programme: Programme
    let windowStart: Date
    let pixelsPerSecond: CGFloat
    let cellHeight: CGFloat
    let onTap: () -> Void

    @State private var isHovered = false

    private var xOffset: CGFloat {
        max(0, CGFloat(programme.start.timeIntervalSince(windowStart)) * pixelsPerSecond)
    }

    private var width: CGFloat {
        let dur = programme.stop.timeIntervalSince(programme.start)
        return max(2, CGFloat(dur) * pixelsPerSecond - 2)
    }

    private var isPast: Bool { programme.stop < Date() }

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                // Background
                cellBackground
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))

                // Progress fill for live
                if programme.isNow {
                    GeometryReader { geo in
                        Rectangle()
                            .fill(NS.accent)
                            .frame(width: geo.size.width * programme.progress, height: 2)
                            .shadow(color: NS.accent.opacity(0.6), radius: 4)
                    }
                    .frame(height: 2)
                }

                // Text
                VStack(alignment: .leading, spacing: 2) {
                    Text(programme.title)
                        .font(NS.Font.caption)
                        .fontWeight(programme.isNow ? .semibold : .regular)
                        .foregroundStyle(programme.isNow ? NS.accent2 : (isPast ? NS.text3 : NS.text))
                        .lineLimit(2)
                    Text(timeStr(programme.start))
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
            }
            .frame(width: width, height: cellHeight)
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.sm)
                    .stroke(
                        programme.isNow ? NS.accentBorder :
                        isHovered ? NS.border3 : NS.border2,
                        lineWidth: programme.isNow ? 1.5 : 0.5
                    )
            )
        }
        .buttonStyle(.plain)
        .opacity(isPast ? 0.45 : 1.0)
        .offset(x: xOffset)
        .onHover { isHovered = $0 }
    }

    @ViewBuilder
    private var cellBackground: some View {
        if programme.isNow {
            LinearGradient(colors: [NS.accentGlow, NS.accent.opacity(0.05)],
                          startPoint: .topLeading, endPoint: .bottomTrailing)
        } else {
            NS.surface2
        }
    }

    private func timeStr(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "HH:mm"; return f.string(from: d)
    }
}

// MARK: - Now line

struct NowLine: View {
    let windowStart: Date
    let slotWidth: CGFloat
    let slotMins: Double
    let rowHeight: CGFloat
    let rowCount: Int
    let timelineH: CGFloat

    @State private var now = Date()
    let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var xOffset: CGFloat {
        let secs = now.timeIntervalSince(windowStart)
        return max(0, CGFloat(secs) / CGFloat(slotMins * 60) * slotWidth)
    }

    private var totalHeight: CGFloat {
        timelineH + CGFloat(rowCount) * rowHeight
    }

    var body: some View {
        ZStack(alignment: .top) {
            // Line
            Rectangle()
                .fill(NS.accent)
                .frame(width: 2, height: totalHeight - timelineH)
                .offset(x: xOffset, y: timelineH)

            // Cap circle
            Circle()
                .fill(NS.accent)
                .frame(width: 10, height: 10)
                .shadow(color: NS.accent, radius: 4)
                .offset(x: xOffset - 4, y: timelineH - 5)
        }
        .allowsHitTesting(false)
        .onReceive(timer) { _ in now = Date() }
    }
}
