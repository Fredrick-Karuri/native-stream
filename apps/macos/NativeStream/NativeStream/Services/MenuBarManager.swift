// MenuBarManager.swift — NS-312
// Manages the menu bar status item with a mini player popover.
// Shows during active playback only.

import SwiftUI
import AppKit

@MainActor
final class MenuBarManager {

    private var statusItem: NSStatusItem?
    private var popover: NSPopover?

    func show(playerVM: PlayerViewModel, epgVM: EPGViewModel) {
        guard statusItem == nil else { return }

        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        item.button?.image = NSImage(systemSymbolName: "play.tv.fill", accessibilityDescription: "NativeStream")
        item.button?.image?.isTemplate = true
        item.button?.action = #selector(statusItemClicked)
        item.button?.target = self

        statusItem = item

        let pop = NSPopover()
        pop.contentSize = NSSize(width: 300, height: 160)
        pop.behavior = .transient
        pop.contentViewController = NSHostingController(
            rootView: MenuBarPopoverView()
                .environment(playerVM)
                .environment(epgVM)
        )
        popover = pop
    }

    func hide() {
        statusItem.map { NSStatusBar.system.removeStatusItem($0) }
        statusItem = nil
        popover = nil
    }

    @objc private func statusItemClicked() {
        guard let button = statusItem?.button, let pop = popover else { return }
        if pop.isShown {
            pop.close()
        } else {
            pop.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        }
    }
}

// MARK: - Popover content

struct MenuBarPopoverView: View {

    @Environment(PlayerViewModel.self) private var playerVM
    @Environment(EPGViewModel.self)    private var epgVM

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let channel = playerVM.currentChannel {
                HStack(spacing: 10) {
                    AsyncImage(url: channel.logoURL) { phase in
                        if case .success(let img) = phase {
                            img.resizable().scaledToFit()
                        } else {
                            Image(systemName: "tv").foregroundStyle(.secondary)
                        }
                    }
                    .frame(width: 32, height: 32)
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    VStack(alignment: .leading, spacing: 2) {
                        Text(channel.name)
                            .font(.headline)
                            .lineLimit(1)
                        if let prog = epgVM.currentProgramme(for: channel) {
                            Text(prog.title)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            ProgressView(value: prog.progress)
                                .progressViewStyle(.linear)
                                .tint(.accentColor)
                        } else {
                            Text("Live")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                HStack(spacing: 16) {
                    Button {
                        playerVM.togglePlayback()
                    } label: {
                        Image(systemName: playerVM.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title2)
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Text(playerVM.isPlaying ? "Playing" : "Paused")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("No channel selected")
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(width: 300)
    }
}