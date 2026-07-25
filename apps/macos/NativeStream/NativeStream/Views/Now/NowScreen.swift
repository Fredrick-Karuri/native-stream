/// Now/NowScreen.swift
///
/// Thin orchestrator for the EPG-first home screen.
/// Delegates bucketing to NowScreenViewModel and layout to
/// section sub-views. Owns only the clock tick and .task triggers.

import SwiftUI
import Combine

struct NowScreen: View {

    @Environment(PlaylistViewModel.self) private var playlistVM
    @Environment(EPGViewModel.self)      private var epgVM

    let onSelectChannel: (Channel) -> Void

    @State private var vm = NowScreenViewModel()

    private let clockTick = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            NowScreenTopBar(liveCount: vm.liveCount, soonCount: vm.soonCount)
            Divider().overlay(NS.border)
            scrollContent
        }
        .background(NS.bg)
        .task(id: playlistVM.channels.count) { vm.recompute(channels: playlistVM.channels, epgVM: epgVM) }
        .task(id: epgVM.stores.count)        { vm.recompute(channels: playlistVM.channels, epgVM: epgVM) }
        .onReceive(clockTick)                { _ in vm.recompute(channels: playlistVM.channels, epgVM: epgVM) }
    }

    // MARK: - Scroll content

    @ViewBuilder
    private var scrollContent: some View {
        if playlistVM.isLoading {
            NowLoadingView()
        } else if vm.liveCount == 0 && vm.soonCount == 0 {
            NowEmptyView()
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: NS.Spacing.xxl) {
                    if !vm.liveMatches.isEmpty  {
                        MatchesLiveSection(items: vm.liveMatches, onSelectChannel: onSelectChannel)
                    }
                    if !vm.liveOnAir.isEmpty    {
                        LiveOnAirSection(items: vm.liveOnAir, onSelectChannel: onSelectChannel)
                    }
                    if !vm.startingSoon.isEmpty {
                        StartingSoonSection(items: vm.startingSoon, onSelectChannel: onSelectChannel)
                    }
                }
                .padding(NS.Spacing.xl)
                .padding(.bottom, 80)
            }
        }
    }
}
