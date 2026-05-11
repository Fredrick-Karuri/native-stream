//
//  ScheduleEventRow.swift
//  NativeStream
import SwiftUI


// MARK: - Schedule Event Row (UX-019)

struct ScheduleEventRow: View {
    let channel: Channel
    let programme: Programme
    let onTap: () -> Void

    @State private var bellOn = false

    private var isLive: Bool { programme.isNow }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: NS.Spacing.md) {

                // Time column
                VStack(spacing: 2) {
                    Text(isLive ? minuteLabel : programme.startTimeString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(isLive ? NS.live : NS.text2)
                        .fontWeight(isLive ? .bold : .regular)
                    if isLive {
                        Text("LIVE")
                            .font(.system(size: NS.Schedule.microLabelSize, weight: .bold))
                            .foregroundStyle(NS.live)
                    }
                }
                .frame(width: NS.Schedule.timeColumnWidth, alignment: .center)

                // Team badges
                if let teams = teamsFromTitle {
                    HStack(spacing: NS.Spacing.xs) {
                        teamBadge(teams.home)
                        Text("vs").font(NS.Font.monoSm).foregroundStyle(NS.text3)
                        teamBadge(teams.away)
                    }
                }

                // Body
                VStack(alignment: .leading, spacing: NS.Spacing.xxs) {
                    Text(programme.title)
                        .font(NS.Font.captionMed)
                        .foregroundStyle(NS.text)
                        .lineLimit(1)
                    Text(channel.name)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.text3)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Right side
                if isLive {
                    NSLiveBadge(isLive: true)
                } else {
                    Text(programme.startTimeString)
                        .font(NS.Font.monoSm)
                        .foregroundStyle(NS.accent)
                }

                // Bell
                Button {
                    bellOn.toggle()
                } label: {
                    Image(systemName: bellOn ? "bell.fill" : "bell")
                        .font(.system(size: NS.Help.inlineIconSize))
                        .foregroundStyle(bellOn ? NS.accent : NS.text3)
                }
                .buttonStyle(.plain)
                .padding(.leading, NS.Spacing.xs)
            }
            .padding(.horizontal, NS.Spacing.md)
            .padding(.vertical, NS.Spacing.sm)
        }
        .buttonStyle(.plain)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.md)
                .stroke(
                    isLive ? NS.live.opacity(0.157) : NS.border,
                    lineWidth: 0.5
                )
        )
    }

    private func teamBadge(_ name: String) -> some View {
        ZStack {
            Circle().fill(NS.surface3).overlay(Circle().stroke(NS.border2, lineWidth: 0.5))
            Text(initials(name)).font(.system(size: NS.Schedule.microLabelSize, weight: .medium)).foregroundStyle(NS.text2)
        }
        .frame(width: NS.Schedule.teamBadgeSize, height: NS.Schedule.teamBadgeSize)
    }

    private func initials(_ name: String) -> String {
        name.components(separatedBy: " ")
            .prefix(2)
            .compactMap { $0.first.map { String($0) } }
            .joined()
            .uppercased()
    }

    private var minuteLabel: String {
        let elapsed = Int(Date().timeIntervalSince(programme.start) / 60)
        return "\(elapsed)'"
    }

    private var teamsFromTitle: (home: String, away: String)? {
        let parts = programme.title.components(separatedBy: " vs ")
        guard parts.count >= 2 else { return nil }
        return (
            parts[0].trimmingCharacters(in: .whitespaces),
            parts[1].components(separatedBy: " — ")[0].trimmingCharacters(in: .whitespaces)
        )
    }
}


