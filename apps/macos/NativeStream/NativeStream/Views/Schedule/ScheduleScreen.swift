// ScheduleScreen.swift — UX-018, FX-004, FX-011
// FX-004: replaced schedule(for:hours:) with schedule(for:from:to:) so future
// dates return correct results. Fixed in both events and eventCount.

import SwiftUI

struct ScheduleScreen: View {

    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(EPGViewModel.self)      private var epgVM

    let onSelectChannel: (Channel) -> Void

    @State private var selectedDate: DateItem = DateItem.today
    @State private var selectedSport: SportCategory? = nil

    // MARK: - Date model

    struct DateItem: Identifiable, Equatable {
        let id: Int
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

        var dayEnd: Date {
            Calendar.current.date(byAdding: .day, value: 1, to: date) ?? date
        }
    }

    private var dates: [DateItem] {
        (0..<7).compactMap { offset in
            guard let d = Calendar.current.date(
                byAdding: .day, value: offset, to: DateItem.today.date
            ) else { return nil }
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
        let from = selectedDate.date
        let to   = selectedDate.dayEnd

        return playlistVM.channels.flatMap { ch in
            epgVM.schedule(for: ch, from: from, to: to)
                .filter { prog in
                    if let sport = selectedSport {
                        return epgVM.matchesSport(sport, programme: prog, channel: ch)
                    }
                    return prog.isSportMatch // ← default to sport only
                }
                .map { EventItem(channel: ch, programme: $0) }
        }
        .sorted { $0.programme.start < $1.programme.start }
    }

    private var groupedEvents: [(label: String, isLive: Bool, items: [EventItem])] {
        var morning: [EventItem] = []
        var afternoon: [EventItem] = []
        var tonight: [EventItem] = []

        for ev in events {
            let hour = Calendar.current.component(.hour, from: ev.programme.start)
            if hour >= 18      { tonight.append(ev) }
            else if hour >= 12 { afternoon.append(ev) }
            else               { morning.append(ev) }
        }

        var result: [(label: String, isLive: Bool, items: [EventItem])] = []
        if !morning.isEmpty   { result.append(("Morning",        false, morning)) }
        if !afternoon.isEmpty { result.append(("This afternoon", false, afternoon)) }
        if !tonight.isEmpty   { result.append(("Tonight",        false, tonight)) }
        return result
    }

    private func eventCount(for date: DateItem) -> Int {
        playlistVM.channels.reduce(0) { count, ch in
            count + epgVM.schedule(for: ch, from: date.date, to: date.dayEnd)
                .filter { $0.isSportMatch }
                .count
        }
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
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: NS.Spacing.xs) {
                    NSChip(label: "All", isActive: selectedSport == nil) {
                        selectedSport = nil
                    }
                    ForEach(SportCategory.allCases, id: \.self) { sport in
                        NSChip(label: sport.label, isActive: selectedSport == sport) {
                            selectedSport = sport
                        }
                    }
                }
            }
            .frame(maxWidth: 480)
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
                    ForEach(dates) { date in dateRow(date) }
                }
                .padding(NS.Spacing.sm)
            }
        }
        .frame(width: 180)
        .background(NS.surface)
    }

    private func dateRow(_ date: DateItem) -> some View {
        let isActive = date == selectedDate
        return Button { selectedDate = date } label: {
            VStack(alignment: .leading, spacing: 2) {
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
                        .padding(.vertical, 1)
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
                Text("📅").font(.system(size: 32))
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
                .padding(.bottom, 80)
            }
        }
    }
}
