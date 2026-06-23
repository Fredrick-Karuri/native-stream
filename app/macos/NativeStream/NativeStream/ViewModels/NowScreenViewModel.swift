/// Features/Now/NowScreenViewModel.swift
///
/// Observable view-model for NowScreen. Owns all bucketing logic:
/// live sport matches, live-on-air non-match channels, and channels
/// starting within the next two hours. Stateless between recompute calls
/// — the view drives refresh via .task and a clock tick.

import Foundation
import Observation

private let startingSoonLookaheadSeconds: TimeInterval = 2 * 3600

@Observable
final class NowScreenViewModel {

    // MARK: - Published buckets

    private(set) var liveMatches:  [(channel: Channel, programme: Programme)] = []
    private(set) var liveOnAir:    [(channel: Channel, programme: Programme)] = []
    private(set) var startingSoon: [(channel: Channel, programme: Programme)] = []

    // MARK: - Derived counts

    var liveCount: Int { liveMatches.count + liveOnAir.count }
    var soonCount: Int { startingSoon.count }

    // MARK: - Recompute

    func recompute(channels: [Channel], epgVM: EPGViewModel) {
        let cutoff = Date().addingTimeInterval(startingSoonLookaheadSeconds)

        liveMatches = channels.compactMap { channel in
            guard let prog = epgVM.currentProgramme(for: channel),
                  prog.isSportMatch,
                  prog.title.contains(" vs ") else { return nil }
            return (channel, prog)
        }

        liveOnAir = channels.compactMap { channel in
            guard let prog = epgVM.currentProgramme(for: channel),
                  !prog.isSportMatch else { return nil }
            return (channel, prog)
        }

        startingSoon = channels.compactMap { channel in
            guard epgVM.currentProgramme(for: channel) == nil,
                  let next = epgVM.nextProgramme(for: channel),
                  next.start <= cutoff else { return nil }
            return (channel, next)
        }
    }
}
