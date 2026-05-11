
// ScheduleScreen.swift — UX-018
// Full programme guide by date, filterable by sport.

import SwiftUI

struct ScheduleScreen: View {

    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(EPGViewModel.self)      private var epgVM

    let onSelectChannel: (Channel) -> Void

    @State private var selectedDate: DateItem = DateItem.today
    @State private var selectedSport: SportCategory? = nil  // nil = all sports

    // MARK: - Date model

    struct DateItem: Identifiable, Equatable {
        let id: Int // offset from today
        let date: Date

        static let today = DateItem(id: 0, date: Calendar.current.startOfDay(for: Date()))

        var dayLabel: String {
            if id == 0 { return "Today" }
            if id == 1 { return "Tomorrow" }
            let f = DateFormatter(); f.dateFormat = "EEEE"
            return f.string(from: date)
        }

        var dateLabel: String {
            let f = DateFormatter(); f.dateFormat = "d MMM"
            return f.string(from: date)
        }
    }

    private var dates: [DateItem] {
        (0..<7).compactMap { offset in
            guard let d = Calendar.current.date(byAdding: .day, value: offset, to: DateItem.today.date)
            else { return nil }
            return DateItem(id: offset, date: d)
        }
    }

    // MARK: - Events

    private struct EventItem: Identifiable {
        let id = UUID()
        let channel: Channel
        let programme: Programme
    }

    private var events: [EventItem] {
        let dayStart = selectedDate.date
        guard let dayEnd = Calendar.current.date(byAdding: .day, value: 1, to: dayStart) else { return [] }

        return playlistVM.channels.flatMap { ch in
            epgVM.schedule(for: ch, hours: 24)
                .filter { $0.start >= dayStart && $0.start < dayEnd }
                .filter { prog in
                    guard let sport = selectedSport else { return true }
                    return sport.epgKeywords.contains {
                        prog.title.lowercased().contains($0) ||
                        ch.groupTitle.lowercased().contains($0)
                    }
                }
                .map { EventItem(channel: ch, programme: $0) }
        }
        .sorted { $0.programme.start < $1.programme.start }
    }

    // Group events into time brackets
    private var groupedEvents: [(label: String, isLive: Bool, items: [EventItem])] {
        let now = Date()
        var live: [EventItem] = []
        var tonight: [EventItem] = []
        var afternoon: [EventItem] = []
        var morning: [EventItem] = []

        for ev in events {
            if ev.programme.isNow {
                live.append(ev)
            } else {
                let hour = Calendar.current.component(.hour, from: ev.programme.start)
                if hour >= 18       { tonight.append(ev) }
                else if hour >= 12  { afternoon.append(ev) }
                else                { morning.append(ev) }
            }
        }

        var result: [(label: String, isLive: Bool, items: [EventItem])] = []
        if !live.isEmpty      { result.append(("Live now",        true,  live)) }
        if !morning.isEmpty   { result.append(("Morning",         false, morning)) }
        if !afternoon.isEmpty { result.append(("This afternoon",  false, afternoon)) }
        if !tonight.isEmpty   { result.append(("Tonight",         false, tonight)) }
        return result
    }

    private func eventCount(for date: DateItem) -> Int {
        guard let dayEnd = Calendar.current.date(byAdding: .day, value: 1, to: date.date) else { return 0 }
        return playlistVM.channels.flatMap {
            epgVM.schedule(for: $0, hours: 24)
                .filter { $0.start >= date.date && $0.start < dayEnd }
        }.count
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Divider().overlay(NS.border)
            HStack(spacing: 0) {
                dateColumn
                Divider().overlay(NS.border)
                eventList
            }
        }
        .background(NS.bg)
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: NS.Spacing.sm) {
            Text("Schedule")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            Spacer()

            // Sport chips — All + dynamic sports
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: NS.Spacing.xs) {
                    NSChip(label: "All sports", isActive: selectedSport == nil) {
                        selectedSport = nil
                    }
                    ForEach(SportCategory.allCases, id: \.self) { sport in
                        NSChip(label: sport.label, isActive: selectedSport == sport) {
                            selectedSport = sport
                        }
                    }
                }
            }
            .frame(maxWidth: NS.Schedule.chipScrollMaxWidth)
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.vertical, NS.Spacing.md)
        .background(NS.surface)
    }

    // MARK: - Date column

    private var dateColumn: some View {
        VStack(spacing: 0) {
            Text("Date")
                .font(NS.Font.label)
                .foregroundStyle(NS.text3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, NS.Spacing.md)
                .padding(.vertical, NS.Spacing.sm)
                .background(NS.surface)

            Divider().overlay(NS.border)

            ScrollView {
                VStack(spacing: 2) {
                    ForEach(dates) { date in
                        dateRow(date)
                    }
                }
                .padding(NS.Spacing.sm)
            }
        }
        .frame(width: NS.Help.sidebarWidth)
        .background(NS.surface)
    }

    private func dateRow(_ date: DateItem) -> some View {
        let isActive = date == selectedDate
        return Button {
            selectedDate = date
        } label: {
            VStack(alignment: .leading, spacing: NS.Spacing.xxs) {
                Text(date.dayLabel)
                    .font(NS.Font.captionMed)
                    .foregroundStyle(isActive ? NS.text : NS.text2)
                Text(date.dateLabel)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.text3)
                let count = eventCount(for: date)
                if count > 0 {
                    Text("\(count) events")
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.accent)
                        .padding(.horizontal, NS.Spacing.sm)
                        .padding(.vertical, NS.Spacing.xxs)
                        .background(NS.accentGlow)
                        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(NS.Spacing.sm)
            .background(isActive ? NS.surface2 : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.md)
                    .stroke(isActive ? NS.border2 : Color.clear, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Event list

    @ViewBuilder
    private var eventList: some View {
        if events.isEmpty {
            VStack(spacing: NS.Spacing.md) {
                Text("📅").font(.system(size: NS.Schedule.emptyEmojiSize))
                Text("Nothing scheduled").font(NS.Font.display).foregroundStyle(NS.text)
                Text("Try a different date or sport filter.")
                    .font(NS.Font.caption).foregroundStyle(NS.text3)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xl) {
                    ForEach(groupedEvents, id: \.label) { group in
                        VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                            HStack(spacing: NS.Spacing.sm) {
                                if group.isLive { NSPulseDot() }
                                NSGroupHeader(title: group.label, count: group.items.count)
                            }
                            VStack(spacing: NS.Spacing.xs) {
                                ForEach(group.items) { item in
                                    ScheduleEventRow(
                                        channel: item.channel,
                                        programme: item.programme
                                    ) { onSelectChannel(item.channel) }
                                }
                            }
                        }
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, NS.Help.emptyTopPadding)
            }
        }
    }
}
