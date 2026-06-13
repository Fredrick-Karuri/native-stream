# NativeStream Android — Tablet Landscape Tickets

**Platform:** Android 8.0+ (API 26+)  
**Stack:** Jetpack Compose · WindowSizeClass · Material3  
**Feature:** Tablet landscape layout fixes across Now, Browse, and Settings screens  
**Last Updated:** 2026-06-10

---

## How to Read This

- **ID** — `AND-LAND-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day)
- **Needs** — files to read before starting
- **Done when** — observable acceptance criteria

---

## EPIC AND-LAND-E01 — Foundation

---

### AND-LAND-001 — Verify WindowSizeClass provision on all screens
- **Effort:** S
- **Needs:** `ui/MainActivity.kt`, `ui/LocalWindowSizeClass.kt`
- **Description:** Confirm `LocalWindowSizeClass` is provided at the root composition and that `Expanded` fires correctly in landscape on a tablet (≥840dp). Add a debug log on size class change so we can verify during development. Ensure `WindowSizeClass` is recalculated on rotation and not cached.
- **Done when:** Logcat confirms `Expanded` on landscape tablet rotation. `Medium`/`Compact` fires correctly on phone.

---

### AND-LAND-002 — Shared `NSAdaptiveScaffold` layout wrapper
- **Effort:** M
- **Needs:** `ui/components/NSComponents.kt`, `ui/theme/NSDimens.kt`, AND-LAND-001
- **Description:** Extract a shared `NSAdaptiveScaffold` composable that wraps the rail + screen content area. On `Compact`/`Medium`, the rail is hidden (bottom nav or top bar only). On `Expanded`, the rail is visible on the left at `dimens.rail.width`. This prevents each screen duplicating rail logic. Mini player slot is a parameter.
- **Done when:** All three screens use `NSAdaptiveScaffold`. Rail shows/hides correctly on rotation without layout jump.

---

## EPIC AND-LAND-E02 — Now Screen

---

### AND-LAND-003 — Now screen: landscape two-column grid
- **Effort:** M
- **Needs:** `ui/screens/now/NowScreen.kt`, AND-LAND-002
- **Description:** On `Expanded`, replace the single-column `LazyColumn` with a two-pane layout: left pane shows the live channel list/grid, right pane shows the selected channel's detail (EPG, watch button). On `Compact`/`Medium`, behaviour is unchanged. Section headers span full width in both layouts.
- **Done when:** Now screen renders two panes in landscape. Selecting a channel populates the right pane. Portrait is unaffected.

---

### AND-LAND-004 — Now screen: hero area scaling in landscape
- **Effort:** S
- **Needs:** `ui/screens/now/NowScreen.kt`, AND-LAND-003
- **Description:** The hero artwork / match banner in the Now screen currently overflows vertically in landscape because the viewport is shorter. Cap hero height to `40%` of window height on `Expanded`. Use `BoxWithConstraints` to derive the cap at runtime.
- **Done when:** Hero does not overflow or clip in landscape. Scrollable content below hero is reachable.

---

## EPIC AND-LAND-E03 — Browse Screen

---

### AND-LAND-005 — Browse screen: filter row in list pane header (Expanded)
- **Effort:** S
- **Needs:** `ui/screens/browse/BrowseScreen.kt`, AND-LAND-002
- **Description:** On `Expanded`, the `BrowseFilterRow` (chips only — pill is in top bar) must render inside the list pane `Column` header, not in the global top area. Currently the filter row still renders above the master-detail split. Ensure the top-level filter row is suppressed when `useDetail = true` and the list pane header version renders correctly with correct width constraints inside the 260dp pane.
- **Done when:** Chips are scoped to the list pane width. No chips appear above the master-detail split on tablet landscape.

---

### AND-LAND-006 — Browse screen: list pane scroll independent of detail pane
- **Effort:** S
- **Needs:** `ui/screens/browse/BrowseScreen.kt`
- **Description:** Verify the `LazyColumn` in the master pane and the `LazyColumn` in `BrowseDetailPane` each have independent scroll state and do not interfere. On some devices the outer `Column` captures scroll events. Wrap master pane in `nestedScroll` if needed. Both panes should scroll independently.
- **Done when:** Scrolling the channel list does not scroll the detail pane and vice versa. Overscroll indicators are correct per pane.

---

### AND-LAND-007 — Browse screen: source picker sheet covers full tablet in landscape
- **Effort:** S
- **Needs:** `ui/screens/browse/BrowseScreen.kt`, `ui/components/NSComponents.kt`
- **Description:** `NSSourcePickerSheet` uses `ModalBottomSheet`. On a landscape tablet it currently renders at half width or misaligned. Set `sheetMaxWidth` to `Dp.Unspecified` on the `ModalBottomSheet` so it spans the full screen width. Verify drag handle and sheet height are correct.
- **Done when:** Picker sheet spans full width in landscape. Dismiss works. Source selection updates the list pane.

---

## EPIC AND-LAND-E04 — Settings Screen

---

### AND-LAND-008 — Settings screen: two-pane master-detail layout
- **Effort:** M
- **Needs:** `ui/screens/settings/SettingsScreen.kt`, AND-LAND-002
- **Description:** On `Compact`/`Medium`, Settings is a full-screen nav list that pushes to detail screens. On `Expanded`, render a persistent two-pane layout: left sidebar (160dp) shows the nav items (Server, Sources, Playback, Proxy); right pane shows the selected section content. No push navigation on tablet — selection updates the right pane in place. Active nav item gets the accent left-border treatment.
- **Done when:** Two-pane renders on tablet landscape. Nav items highlight correctly. Rotating back to portrait restores single-pane navigation.

---

### AND-LAND-009 — Settings sources pane: landscape card layout
- **Effort:** S
- **Needs:** `ui/screens/settings/SettingsSourcesScreen.kt`, AND-LAND-008
- **Description:** In the right pane of the tablet settings layout, the Sources list renders `SourceCard` items (dot + name + URL + channel count + action buttons). Matches the design: colored dot, `source.name` in `captionMedium`, URL in `mono`, refresh/edit/delete icon buttons. "Add source" is a dashed full-width button at the bottom. No changes to phone layout.
- **Done when:** Source cards render correctly in the right pane. Action buttons work. Add source opens `AddSourceSheet`.

---

### AND-LAND-010 — Settings: keyboard avoidance in landscape
- **Effort:** S
- **Needs:** `ui/screens/settings/SettingsScreen.kt`, AND-LAND-008
- **Description:** In landscape the viewport is shorter. When a text field (server URL, EPG URL) is focused the keyboard covers the input. Add `imePadding()` to the settings content pane and ensure the `LazyColumn`/`Column` scrolls the focused field into view.
- **Done when:** Focused text field is visible above the keyboard in landscape. No content is permanently hidden.

---

## Dependency Order

```
AND-LAND-001   WindowSizeClass verification        ← unblocks everything
AND-LAND-002   NSAdaptiveScaffold wrapper
AND-LAND-003   Now screen two-column
AND-LAND-004   Now screen hero scaling
AND-LAND-005   Browse filter row in list pane
AND-LAND-006   Browse independent scroll
AND-LAND-007   Browse picker sheet full width
AND-LAND-008   Settings two-pane layout
AND-LAND-009   Settings sources card layout
AND-LAND-010   Settings keyboard avoidance
```

---

## Summary

| Epic | Tickets | Effort |
|---|---|---|
| AND-LAND-E01 Foundation | 001–002 | ~0.5 days |
| AND-LAND-E02 Now screen | 003–004 | ~0.5 days |
| AND-LAND-E03 Browse screen | 005–007 | ~0.5 days |
| AND-LAND-E04 Settings screen | 008–010 | ~1 day |
| **Total** | **10 tickets** | **~2.5 days** |

---

## Test Matrix

| Scenario | Compact (portrait) | Medium (portrait) | Expanded (landscape) |
|---|---|---|---|
| Rail visible | ✗ | ✗ | ✓ |
| Now — two pane | ✗ | ✗ | ✓ |
| Now — hero height capped | — | — | ✓ |
| Browse — chips in list pane | ✗ | ✗ | ✓ |
| Browse — independent scroll | — | — | ✓ |
| Browse — picker full width | — | — | ✓ |
| Settings — two pane | ✗ | ✗ | ✓ |
| Settings — source cards | — | — | ✓ |
| Settings — keyboard avoidance | — | ✓ | ✓ |
| Rotation preserves state | ✓ | ✓ | ✓ |