// ChannelRow.swift
// Single row in the channel sidebar with logo, name, now/next EPG info.

import SwiftUI

struct ChannelRow: View {

    @Environment(EPGViewModel.self)   private var epgVM
    @Environment(SettingsStore.self)  private var settings

    let channel: Channel
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 10) {
            // Channel logo
            AsyncImage(url: channel.logoURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit()
                case .failure, .empty:
                    Image(systemName: "tv")
                        .foregroundStyle(.secondary)
                @unknown default:
                    EmptyView()
                }
            }
            .frame(width: 32, height: 32)
            .clipShape(RoundedRectangle(cornerRadius: 6))

            // Name + EPG
            VStack(alignment: .leading, spacing: 2) {
                Text(channel.name)
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(1)

                if let prog = epgVM.currentProgramme(for: channel) {
                    HStack(spacing: 4) {
                        Text(prog.title)
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)

                        Spacer()

                        // Progress bar
                        ProgressView(value: prog.progress)
                            .progressViewStyle(.linear)
                            .frame(width: 36)
                            .tint(isSelected ? .white.opacity(0.8) : .accentColor)
                    }
                } else {
                    Text(channel.groupTitle)
                        .font(.system(size: 11))
                        .foregroundStyle(.tertiary)
                }
            }

            Spacer(minLength: 0)

            // Favourite star
            Button {
                settings.toggleFavourite(channel)
            } label: {
                Image(systemName: settings.isFavourite(channel) ? "star.fill" : "star")
                    .font(.system(size: 11))
                    .foregroundStyle(settings.isFavourite(channel) ? .yellow : .secondary)
            }
            .buttonStyle(.plain)
            .opacity(isSelected ? 1 : 0)
            .animation(.easeInOut(duration: 0.15), value: isSelected)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}
