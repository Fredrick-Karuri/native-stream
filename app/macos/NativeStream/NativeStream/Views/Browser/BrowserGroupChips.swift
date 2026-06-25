//
//  BrowserGroupChips.swift
//  NativeStream
//
//  Created by Fredrick Karuri on 25/06/2026.
//
import SwiftUI

struct BrowserGroupChips: View {

    let allGroupNames:      [String]
    let subGroupNames:      [String]
    let activeSports:       [SportCategory]
    let selectedSource:     PlaylistSource?
    let selectedGroup:      String?
    let selectedSubGroup:   String?
    let selectedSport:      SportCategory?
    let showFavouritesOnly: Bool
    let onSelectAll:        () -> Void
    let onSelectGroup:      (String) -> Void
    let onSelectSubGroup:   (String?) -> Void
    let onSelectSport:      (SportCategory?) -> Void
    let onToggleFavourites: () -> Void

    private var showSubGroups: Bool {
        selectedSource != nil && !selectedSource!.isAll
            && selectedGroup != nil && !subGroupNames.isEmpty
    }

    private var showSportChips: Bool {
        !activeSports.isEmpty && !showSubGroups
    }

    var body: some View {
        VStack(spacing: 0) {
            // Level 1 — group chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: NS.Spacing.xs) {
                    NSChip(
                        label:    "All",
                        isActive: selectedGroup == nil && !showFavouritesOnly
                    ) { onSelectAll() }

                    NSChip(
                        label:    "Favourites",
                        isActive: showFavouritesOnly,
                        icon:     "star.fill"
                    ) { onToggleFavourites() }

                    ForEach(allGroupNames, id: \.self) { group in
                        NSChip(label: group, isActive: selectedGroup == group) {
                            onSelectGroup(group)
                        }
                    }
                }
                .padding(.horizontal, NS.Spacing.xl)
                .padding(.vertical, NS.Spacing.sm)
            }
            .background(NS.surface)

            // Level 2 — sub-group chips
            if showSubGroups {
                Divider().overlay(NS.border)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: NS.Spacing.xs) {
                        NSChip(label: "All", isActive: selectedSubGroup == nil) {
                            onSelectSubGroup(nil)
                        }
                        ForEach(subGroupNames, id: \.self) { sub in
                            NSChip(label: sub, isActive: selectedSubGroup == sub) {
                                onSelectSubGroup(sub)
                            }
                        }
                    }
                    .padding(.horizontal, NS.Spacing.xl)
                    .padding(.vertical, NS.Spacing.xs)
                }
                .background(NS.surface)
                .transition(.asymmetric(
                    insertion: .push(from: .top).combined(with: .opacity),
                    removal:   .push(from: .bottom).combined(with: .opacity)
                ))
            }

            // Level 2 — sport chips (shown when sport group active, no sub-groups)
            if showSportChips {
                Divider().overlay(NS.border)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: NS.Spacing.xs) {
                        NSChip(
                            label:    "All Sports",
                            isActive: selectedSport == nil
                        ) { onSelectSport(nil) }

                        ForEach(activeSports, id: \.self) { sport in
                            NSChip(
                                label:    sport.label,
                                isActive: selectedSport == sport
                            ) { onSelectSport(sport) }
                        }
                    }
                    .padding(.horizontal, NS.Spacing.xl)
                    .padding(.vertical, NS.Spacing.xs)
                }
                .background(NS.surface)
                .transition(.asymmetric(
                    insertion: .push(from: .top).combined(with: .opacity),
                    removal:   .push(from: .bottom).combined(with: .opacity)
                ))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showSubGroups)
        .animation(.easeInOut(duration: 0.2), value: showSportChips)
    }
}
