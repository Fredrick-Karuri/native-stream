# NativeStream Android — Tablet & Responsiveness Tickets

**Platform:** Android 8.0+ (API 26+) · Phones + Tablets (600dp+) + Foldables  
**Stack:** Jetpack Compose adaptive layouts · WindowSizeClass · NavigationSuiteScaffold  
**Last Updated:** 2026-06-07  
**Principle:** One codebase. Layout adapts to the window. No separate tablet APK.

---

## How to Read This

- **ID** — `AND-TABLET-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day), XL (2+ days)
- **Needs** — files to read before starting
- **Window class** — `Compact` (< 600dp) · `Medium` (600–840dp) · `Expanded` (840dp+)
- **Done when** — observable acceptance criteria

---

## Background: WindowSizeClass Breakpoints

| Class | Width | Typical device |
|---|---|---|
| Compact | < 600dp | Phone portrait |
| Medium | 600–840dp | Phone landscape, small tablet |
| Expanded | ≥ 840dp | Large tablet, foldable open |

All layout decisions key off `WindowWidthSizeClass`. Height class used only where relevant (e.g. short landscape phones).

---

## EPIC AND-TABLET-E01 — Foundation

---

### AND-TABLET-001 — WindowSizeClass integration
- **Effort:** S
- **Needs:** `MainActivity.kt`, `AppNavHost.kt`, `NSTheme.kt`
- **Description:** Add `androidx.compose.material3:material3-window-size-class` dependency. Compute `WindowSizeClass` in `MainActivity` and provide it via `CompositionLocal` so all screens can read it without threading through parameters.
- **Implementation:**
  ```kotlin
  val windowSizeClass = calculateWindowSizeClass(this)
  CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
      NSTheme { AppNavHost() }
  }
  ```
- **Done when:** `LocalWindowSizeClass.current` readable from any composable. No visual change on phone.

---

### AND-TABLET-002 — Adaptive navigation shell
- **Effort:** M
- **Needs:** `AppNavHost.kt`, `NSBottomNavBar.kt`, `AppDestination.kt`, `NSDimens.kt`
- **Description:** Replace the fixed `NSBottomNavBar` with `NavigationSuiteScaffold` (Material3) which automatically renders bottom bar on Compact, navigation rail on Medium/Expanded. Matches the existing Mac rail pattern on large screens.
- **Compact:** existing bottom nav (unchanged)
- **Medium/Expanded:** left navigation rail with icon + label, matching `NS.Rail` sizing tokens
- **Done when:** Rotating a tablet to landscape shows the rail. Rotating back shows the bottom bar. Active destination persists through rotation.

---

### AND-TABLET-003 — Safe area + edge-to-edge on all form factors
- **Effort:** S
- **Needs:** `MainActivity.kt`, `AppNavHost.kt`
- **Description:** Ensure `WindowInsets` are consumed correctly on tablets (no content hidden behind system bars or camera cutouts). Apply `Modifier.windowInsetsPadding()` at scaffold level, not per-screen.
- **Done when:** No content clipped on Samsung tablet, Pixel Fold, and a standard foldable emulator.

---

## EPIC AND-TABLET-E02 — Now Screen

---

### AND-TABLET-004 — Now screen two-column layout (Expanded)
- **Effort:** M
- **Needs:** `NowScreen.kt`, `NowBuckets.kt`, `MatchCards.kt`, `LiveOnAirAndSoonCards.kt`
- **Description:** On `Expanded` width, the Now screen splits into two columns:
    - Left (weight 1): Matches live section (hero + small grid)
    - Right (weight 1): Live on air + Starting soon stacked vertically
    - On `Compact`/`Medium`: existing single-column `LazyColumn` unchanged
- **Done when:** Both columns scroll independently. Section headers visible in both. Tapping any item plays the channel.

---

### AND-TABLET-005 — Match hero card wider art area
- **Effort:** S
- **Needs:** `MatchCards.kt`, `NSDimens.kt`
- **Description:** On `Expanded`, `MatchHeroCard` art area height increases from `dimens.match.heroArtHeight` to `180dp` to use the available horizontal space. Score overlay remains centred.
- **Done when:** Hero card fills its column width at correct aspect. Score readable at larger size.

---

## EPIC AND-TABLET-E03 — Browse Screen

---

### AND-TABLET-006 — Adaptive channel grid columns
- **Effort:** S
- **Needs:** `BrowseScreen.kt`, `ChannelCard.kt`, `ChannelLogoView.kt`
- **Description:** Replace the fixed 2-column `chunked(2)` grid with `LazyVerticalGrid(GridCells.Adaptive(minSize = 160.dp))`. This gives:
    - Compact: 2 columns
    - Medium: 3–4 columns
    - Expanded: 4–5 columns
- **Done when:** Column count changes correctly at each breakpoint. Cards maintain 16:9 logo ratio at all widths.

---

### AND-TABLET-007 — Browse master-detail layout (Expanded)
- **Effort:** L
- **Needs:** `BrowseScreen.kt`, `ChannelCard.kt`, `EpgViewModel.kt`, `PlaylistViewModel.kt`, `NSColors.kt`, `NSDimens.kt`
- **Description:** On `Expanded`, Browse becomes a two-pane layout:
    - Left pane (fixed 320dp): group chips + channel list (single column)
    - Right pane (flex): channel detail — current programme, schedule for next 6h, "Watch now" button
    - Tapping a channel in the list updates the right pane without navigating away
    - On `Compact`/`Medium`: existing full-screen grid unchanged
- **Done when:** Selecting a channel updates right pane. "Watch now" opens player. Back on left pane deselects. Rotation preserves selection.

---

### AND-TABLET-008 — MatchDayScreen adaptive grid
- **Effort:** S
- **Needs:** `MatchDayScreen.kt`, `MatchItem.kt`, `MatchCardVariant.kt`
- **Description:** `MatchDayScreen` match card grid uses `GridCells.Adaptive(minSize = 280.dp)` so it goes from 1-column on phone to 2–3 columns on tablet. Match cards should never exceed 400dp wide.
- **Done when:** Cards reflow correctly. Competition labels readable at all widths.

---

## EPIC AND-TABLET-E04 — Player Screen

---

### AND-TABLET-009 — Player landscape persistent sidebar
- **Effort:** M
- **Needs:** `PlayerScreen.kt`, `PlayerSidebar.kt`, `PlayerViewModel.kt`, `NSDimens.kt`
- **Description:** On `Expanded` in landscape, the player sidebar is **always visible** (not collapsible) — mirrors the existing Mac player design. On `Compact`/`Medium`, existing slide-in behaviour unchanged. Sidebar width: `dimens.player.sidebarWidth` (230dp).
- **Done when:** Sidebar visible without toggle on tablet landscape. Sidebar hidden/toggleable on phone. Channel switching works in both modes.

---

### AND-TABLET-010 — Player controls sizing
- **Effort:** S
- **Needs:** `PlayerControls.kt`, `NSDimens.kt`, `NSColors.kt`
- **Description:** On `Expanded`, player control buttons scale up — primary control to 52dp, secondary to 44dp — using the existing `dimens.player` tokens. Icon sizes scale proportionally.
- **Done when:** Controls visually balanced on a 10" tablet. Touch targets ≥ 48dp on all form factors (Material accessibility requirement).

---

## EPIC AND-TABLET-E05 — Settings Screen

---

### AND-TABLET-011 — Settings sidebar-panel layout (Medium/Expanded)
- **Effort:** M
- **Needs:** `SettingsScreen.kt`, `SettingsSections.kt`, `SettingsViewModel.kt`, `NSDimens.kt`
- **Description:** On `Medium`/`Expanded`, restore the sidebar-panel layout (section nav on left, content on right) that was intentionally removed for the phone design. On `Compact`, keep the single scrollable column.
- **Sidebar width:** `dimens.settings.sidebarWidth` (200dp)
- **Panel:** scrollable, full remaining width
- **Done when:** Sidebar shows on tablet. Single column on phone. Active section highlighted. Settings persist regardless of layout.

---

### AND-TABLET-012 — Settings form field widths
- **Effort:** S
- **Needs:** `SettingsSections.kt`, `NSTextField.kt`, `NSDimens.kt`
- **Description:** On `Expanded`, text fields (server URL, EPG URL, source URL) are capped at 480dp width and left-aligned, rather than stretching full width. Prevents unreadably wide text fields.
- **Done when:** Fields capped on tablet. Full-width on phone. No layout shift during input.

---

## EPIC AND-TABLET-E06 — Foldables

---

### AND-TABLET-013 — Foldable hinge avoidance
- **Effort:** M
- **Needs:** `AppNavHost.kt`, `PlayerScreen.kt`, `NowScreen.kt` — plus `androidx.window:window` (Jetpack Window Manager)
- **Description:** On foldable devices in tabletop or book posture, avoid placing interactive content across the hinge. Use `DisplayFeature` to detect hinge position and apply `Modifier.windowInsetsPadding()` accordingly.
- **Done when:** Player controls not split by hinge in tabletop posture. Now screen content not bisected in book posture.

---

### AND-TABLET-014 — Foldable tabletop player mode
- **Effort:** M
- **Needs:** `PlayerScreen.kt`, `PlayerControls.kt`, `PlayerViewModel.kt`, `EpgViewModel.kt` — plus `androidx.window:window`
- **Description:** When a foldable is in tabletop posture (half-open, horizontal hinge):
    - Top half: video playback
    - Bottom half: player controls + programme info + mini EPG
    - This is the "laptop mode" — natural for watching with the device propped up
- **Done when:** Posture detected via `FoldingFeature`. Layout switches automatically. Rotating to flat or fully open restores standard layout.

---

## Dependency Order

```
AND-TABLET-001   WindowSizeClass foundation  ← unblocks everything
AND-TABLET-002   Adaptive nav shell
AND-TABLET-003   Safe areas
AND-TABLET-004   Now screen two-column
AND-TABLET-005   Hero card
AND-TABLET-006   Browse grid adaptive         ← quick win, high impact
AND-TABLET-007   Browse master-detail
AND-TABLET-008   MatchDay grid
AND-TABLET-009   Player persistent sidebar
AND-TABLET-010   Player control sizing
AND-TABLET-011   Settings layout
AND-TABLET-012   Settings field widths
AND-TABLET-013   Foldable hinge
AND-TABLET-014   Foldable tabletop
```

---

## Summary

| Epic | Tickets | Effort |
|---|---|---|
| AND-TABLET-E01 Foundation | 001–003 | ~1 day |
| AND-TABLET-E02 Now screen | 004–005 | ~1 day |
| AND-TABLET-E03 Browse | 006–008 | ~1.5 days |
| AND-TABLET-E04 Player | 009–010 | ~1 day |
| AND-TABLET-E05 Settings | 011–012 | ~0.5 days |
| AND-TABLET-E06 Foldables | 013–014 | ~1.5 days |
| **Total** | **14 tickets** | **~6.5 days** |

---

## Test Devices / Emulators

| Device | WindowClass | Priority |
|---|---|---|
| Pixel 8 (portrait) | Compact | Must pass |
| Pixel 8 (landscape) | Medium | Must pass |
| Pixel Tablet | Expanded | Must pass |
| Samsung Galaxy Tab S9 | Expanded | Should pass |
| Pixel Fold (closed) | Compact | Should pass |
| Pixel Fold (open) | Expanded | Should pass |
| Pixel Fold (tabletop) | Foldable posture | AND-TABLET-014 |