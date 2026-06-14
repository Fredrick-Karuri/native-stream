# NativeStream Android — Performance Tickets

**Platform:** Android 8.0+ (API 26+)  
**Focus:** EPG lookup latency, recomposition cost, memory, O(n) → O(1) hotspots  
**Last Updated:** 2026-06-13

---

## How to Read This

- **ID** — `AND-PERF-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day)
- **Complexity** — current → target
- **Done when** — observable acceptance criteria

---

## EPIC AND-PERF-E01 — EPG Lookup Layer

---

### AND-PERF-001 — Precompute current/next programme index in EpgStore
- **Effort:** M
- **Complexity:** O(n) per channel per frame → O(1)
- **Needs:** `data/parser/EpgStore.kt`, `domain/model/Programme.kt`
- **Description:** `currentProgramme(tvgId)` calls `firstOrNull { it.isNow }` which scans all programmes for that channel on every call. `isNow` itself calls `System.currentTimeMillis()` on every access. With 764 channels rendered in cards, this runs ~764 times per recomposition frame. Fix: at `EpgStore` construction time, build a `Map<String, Programme?>` of current programmes by scanning once. Expose a `rebuildCurrentIndex(nowMs: Long)` function called by `EpgViewModel` on a 30-second timer. Same for `nextProgramme` — precompute a `Map<String, Programme?>` next-index at construction.
- **Done when:** `currentProgramme()` and `nextProgramme()` are O(1) map lookups. Index rebuilt every 30s via coroutine timer in `EpgViewModel`. No `System.currentTimeMillis()` called per card per frame.

---

### AND-PERF-002 — Cache programmeCount at EpgStore construction
- **Effort:** S
- **Complexity:** O(n) per access → O(1)
- **Needs:** `data/parser/EpgStore.kt`
- **Description:** `programmeCount` calls `sumOf { it.size }` on every access — called in `logLoadSummary` and `NSSourcePickerSheet` channel count display. Cache as a `val` computed once at construction.
- **Done when:** `programmeCount` is a stored `Int`, not a computed property.

---

### AND-PERF-003 — Pass nowMs into Programme computed properties
- **Effort:** S
- **Complexity:** `System.currentTimeMillis()` per property access → single call per render cycle
- **Needs:** `domain/model/Programme.kt`, `data/parser/EpgStore.kt`
- **Description:** `Programme.isNow`, `Programme.progress`, and `Programme.timeRemainingString` each call `System.currentTimeMillis()` independently. A single channel card may trigger all three in one recomposition. Fix: add `fun isNow(nowMs: Long)`, `fun progress(nowMs: Long)`, `fun timeRemainingString(nowMs: Long)` overloads that accept a pre-captured timestamp. `EpgViewModel` captures `nowMs` once per index rebuild cycle and passes it through. Keep the no-arg versions as convenience wrappers for call-sites that don't need the optimised path.
- **Done when:** Card rendering calls `programme.progress(nowMs)` not `programme.progress`. Single `System.currentTimeMillis()` per render cycle per screen.

---

## EPIC AND-PERF-E02 — ViewModel Computation

---

### AND-PERF-004 — Move NowScreen bucket computation to EpgViewModel
- **Effort:** M
- **Complexity:** O(n) × 3 on every `epgReady` recomposition → computed once in VM, exposed as StateFlow
- **Needs:** `ui/screens/now/NowScreen.kt`, `ui/viewmodel/EpgViewModel.kt`, `data/parser/NowBuckets.kt`
- **Description:** `liveMatches`, `liveOnAir`, and `startingSoon` are computed inside `NowScreen` via `remember(channels, epgReady)`. This runs three full channel-list scans on the main thread on every recomposition triggered by `epgReady` changing. Move to `EpgViewModel` as `StateFlow<List<ChannelWithProgramme>>` computed via `combine(channels, _isReady)`. Emit on the IO dispatcher, collect on main. `NowScreen` becomes a pure collector.
- **Done when:** `NowScreen` has no `remember` blocks for bucket computation. Buckets update reactively from VM flows. No main-thread O(n) scan on recomposition.

---

### AND-PERF-005 — Move BrowseScreen filtering to PlaylistViewModel
- **Effort:** M
- **Complexity:** O(n) filter chain on every keystroke recomposition → debounced StateFlow in VM
- **Needs:** `ui/screens/browse/BrowseScreen.kt`, `ui/viewmodel/PlaylistViewModel.kt`
- **Description:** `filtered` and `groupedSections` in `BrowseScreen` are computed via `remember` with 6 and 2 keys respectively. Every state change (search keystroke, chip tap, favourites toggle) triggers a full O(n) filter chain on the composition thread. Fix: expose `filteredSections: StateFlow<List<ChannelSection>>` from `PlaylistViewModel` driven by `combine` over `filteredChannels`, `selectedGroup`, `selectedSubGroup`, `selectedSport`, `searchQuery`, `showFavouritesOnly`, `favouriteIds`. Add `setSearchQuery(String)` with a 150ms `debounce` to avoid per-keystroke recomputation. `BrowseScreen` collects `filteredSections` directly — no `remember` blocks for filtering.
- **Done when:** `BrowseScreen` has no filter `remember` blocks. Search is debounced 150ms. `groupedSections` is a StateFlow. Filter logic is testable in isolation.

---

### AND-PERF-006 — FavouriteIds as Set not List in StateFlow
- **Effort:** S
- **Complexity:** O(n) `contains` per card → O(1)
- **Needs:** `ui/viewmodel/FavouritesViewModel.kt`, `data/local/FavouritesDataStore.kt` (or equivalent)
- **Description:** If `favouriteIds` is stored/emitted as `List<String>`, every `favourites.contains(channel.id)` call in `ChannelCard` and `MasterPaneRow` is O(n). Ensure `favouriteIds: StateFlow<Set<String>>` emits a `HashSet` so all `contains` calls are O(1). Verify the DataStore persistence layer stores as a set-safe structure (JSON array deduplicated on write).
- **Done when:** `favouriteIds` type is `StateFlow<Set<String>>`. No `List.contains` in card rendering paths.

---

## EPIC AND-PERF-E03 — Recomposition Guards

---

### AND-PERF-007 — Stable keys and derivedStateOf in BrowseScreen
- **Effort:** S
- **Complexity:** unnecessary recompositions → scoped recomposition
- **Needs:** `ui/screens/browse/BrowseScreen.kt`
- **Description:** Several `remember` blocks in `BrowseScreen` use raw state values as keys (e.g. `remember(channels, selectedGroup, ...)`). When `channels` is a large list, equality checks on the key are O(n). Replace filter-chain `remember` blocks with `derivedStateOf { }` which only triggers recomposition when the derived value actually changes, not when any input key reference changes. Use `key(channel.id)` in `LazyColumn` items (already done). Ensure `ChannelSection` is a `data class` (already done) so structural equality works.
- **Done when:** After AND-PERF-005, remaining `remember` blocks in `BrowseScreen` use `derivedStateOf`. No large-list equality checks as `remember` keys.

---

### AND-PERF-008 — Hoist EPG reads out of LazyColumn item scope
- **Effort:** S
- **Complexity:** EPG lookup per item per frame → lookup once per visible item on scroll settle
- **Needs:** `ui/screens/browse/ChannelCard.kt`, `ui/screens/now/NowScreen.kt`
- **Description:** `epgViewModel.currentProgramme(channel)` is called inside `ChannelCard` which is inside a `LazyColumn`/`LazyVerticalGrid` item. After AND-PERF-001 this becomes O(1), but the call still happens on every recomposition of every visible card. Wrap EPG reads in `remember(channel.id, epgViewModel.isReady)` inside `ChannelCard` so the result is cached per card between recompositions. Clear cache when `isReady` changes (i.e. after index rebuild).
- **Done when:** EPG reads in `ChannelCard` are wrapped in `remember`. Recomposition from unrelated state changes does not re-query EPG per card.

---

## EPIC AND-PERF-E04 — Parse & Load

---

### AND-PERF-009 — M3U parse: pre-size channel list capacity
- **Effort:** S
- **Complexity:** ArrayList resizing → pre-sized allocation
- **Needs:** `data/parser/M3uParser.kt`
- **Description:** `mutableListOf<Channel>()` starts at default ArrayList capacity (10) and resizes repeatedly for large playlists (764+ channels). Pre-size to a reasonable estimate: `ArrayList(INITIAL_CHANNEL_CAPACITY)` with `const val INITIAL_CHANNEL_CAPACITY = 512`. Eliminates repeated array copy on resize for typical playlist sizes.
- **Done when:** Channel list initialised with pre-sized capacity. No functional change — parser output identical.

---

### AND-PERF-010 — EpgStore: sort programmes by startEpochMs at construction
- **Effort:** S
- **Complexity:** O(n) unsorted scan in schedule() → O(log n) binary search possible
- **Needs:** `data/parser/EpgStore.kt`
- **Description:** `schedule()` filters `programmesFor(tvgId)` with a range predicate — O(n). If programmes are sorted by `startEpochMs` at `EpgStore` construction, `schedule()` can use `binarySearch` to find the start index and stop early at the first programme past `toEpochMs`. Sort once at parse time, query many times. Also benefits `nextProgramme` which currently uses `minByOrNull` — with sorted data it becomes `firstOrNull { it.startEpochMs > nowMs }` early exit.
- **Done when:** Programmes sorted ascending by `startEpochMs` per channel at `EpgStore` construction. `schedule()` and `nextProgramme()` use early-exit linear scan or binary search. `minByOrNull` removed from `nextProgramme`.

---

## Dependency Order

```
AND-PERF-001   EPG current/next index          ← unblocks 003, 008
AND-PERF-002   programmeCount cache
AND-PERF-003   nowMs threading                 ← needs 001
AND-PERF-004   NowScreen VM buckets
AND-PERF-005   BrowseScreen VM filtering       ← unblocks 007
AND-PERF-006   FavouriteIds as Set
AND-PERF-007   derivedStateOf guards           ← needs 005
AND-PERF-008   ChannelCard EPG remember        ← needs 001
AND-PERF-009   M3U pre-size
AND-PERF-010   EpgStore sorted programmes
```

---

## Summary

| Epic | Tickets | Effort | Impact |
|---|---|---|---|
| AND-PERF-E01 EPG lookup | 001–003 | ~1 day | Critical — O(n)→O(1) per card per frame |
| AND-PERF-E02 VM compute | 004–006 | ~1 day | High — removes main-thread filter scans |
| AND-PERF-E03 Recomposition | 007–008 | ~0.5 days | Medium — reduces unnecessary redraws |
| AND-PERF-E04 Parse & load | 009–010 | ~0.5 days | Low — one-time cost improvement |
| **Total** | **10 tickets** | **~3 days** | |

---

## Expected Gains

| Metric | Before | Target |
|---|---|---|
| `currentProgramme()` cost | O(n) per card per frame | O(1) map lookup |
| `nextProgramme()` cost | O(n) + `minByOrNull` | O(1) map lookup |
| `schedule()` cost | O(n) scan | O(log n) binary search |
| NowScreen bucket compute | Main thread on recompose | IO thread, StateFlow |
| Browse filter compute | Main thread per keystroke | Debounced 150ms, StateFlow |
| `favouriteIds.contains` | O(n) if List | O(1) HashSet |
| `System.currentTimeMillis()` calls per frame | ~3 × visible cards | 1 per render cycle |