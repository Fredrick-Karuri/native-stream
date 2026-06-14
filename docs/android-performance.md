# NativeStream Android — Performance

> Why the app is fast, what decisions were made, and what to avoid when adding features.

---

## Guiding Principle

**The UI thread only collects. It never computes.**

Every filter, sort, bucket computation, and EPG lookup happens on the IO dispatcher inside a ViewModel. Compose screens receive pre-computed `StateFlow` values and render them. This is the rule that prevents jank.

---

## EPG Lookups — O(1)

The most critical performance decision in the codebase. With 764 channels each rendered as a card, EPG lookups must be O(1) — not O(n).

### The problem (before AND-PERF-001)
```kotlin
// EpgStore — called per card per recomposition
fun currentProgramme(tvgId: String): Programme? =
    programmesFor(tvgId).firstOrNull { it.isNow }  // O(n) scan per channel
```
With 44,931 programmes across 764 channels, this was ~60 programme comparisons per channel per frame.

### The solution — precomputed index
```kotlin
// EpgStore — rebuilt every 30s, O(1) at query time
fun currentProgramme(tvgId: String): Programme? =
    currentIndex[tvgId] ?: currentIndex[tvgId.lowercase()]  // O(1) map lookup
```

The index is built once per 30-second cycle on the IO dispatcher:
```kotlin
// O(n total programmes) — runs once, serves many
fun rebuildIndex(nowMs: Long) {
    programmesByChannelId.forEach { (tvgId, programmes) ->
        // programmes are sorted — early exit once past nowMs
        for (prog in programmes) {
            if (prog.stopEpochMs <= nowMs) continue
            if (prog.startEpochMs <= nowMs) { currentProg = prog }
            else { nextProg = prog; break }   // ← early exit
        }
    }
}
```

### Sort-once, scan-many (AND-PERF-010)
Programmes are sorted by `startEpochMs` at `EpgStore` construction. This enables:
- Early-exit in `rebuildIndex()` — stop scanning once past `nowMs`
- Early-exit in `schedule()` — stop once `startEpochMs >= toEpochMs`
- `nextProgramme` uses `firstOrNull` not `minByOrNull`

---

## Timestamp Capture — one per cycle

`System.currentTimeMillis()` called per-property in `Programme` was hitting every visible card multiple times per frame.

### Before (AND-PERF-003)
```kotlin
val progress: Double get() {
    val nowMs = System.currentTimeMillis()   // called per card
    ...
}
val isNow: Boolean get() {
    val nowMs = System.currentTimeMillis()   // called again per card
    ...
}
```

### After
```kotlin
// EpgViewModel — captured once per 30s rebuild cycle
private val _nowMs = MutableStateFlow(System.currentTimeMillis())
val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

// ChannelCard — uses pre-captured timestamp
val nowMs by epgViewModel.nowMs.collectAsState()
NSProgressBar(value = programme.progress(nowMs).toFloat())
```

---

## Filter Computation — debounced StateFlow

### Before (AND-PERF-005)
```kotlin
// BrowseScreen — runs on composition thread on every keystroke
val filtered = remember(channels, selectedGroup, selectedSubGroup,
                        selectedSport, searchText, showFavouritesOnly, favouriteIds) {
    channels.filter { ... }.filter { ... }.filter { ... }
}
```

### After
```kotlin
// PlaylistViewModel — runs on IO dispatcher, debounced
val filteredSections: StateFlow<List<ChannelSection>> = combine(
    filteredChannels,
    _searchQuery.debounce(150).distinctUntilChanged(),
    _selectedGroup, _selectedSubGroup, _selectedSport,
    _showFavouritesOnly, _favouriteIds,
) { args -> /* filter + group */ }
.stateIn(scope = viewModelScope, started = WhileSubscribed(5_000), ...)

// BrowseScreen — pure collection
val filteredSections by playlistViewModel.filteredSections.collectAsState()
```

Search is debounced 150ms — a full filter chain does not run per keystroke.

---

## Recomposition Guards

### `remember` for EPG reads in cards (AND-PERF-008)
```kotlin
// ChannelCard — EPG not re-queried on unrelated state changes
val programme = remember(channel.id, epgReady) {
    epgViewModel.currentProgramme(channel)
}
```
Only invalidated when `epgReady` changes (index rebuild every 30s), not on scroll, search, or other state changes.

### `derivedStateOf` for list-derived values (AND-PERF-007)
```kotlin
// BrowseScreen — recomposition only when value actually changes
val groups by remember { derivedStateOf {
    channels.map { it.groupTitle }.distinct().sorted()
} }
```
Without `derivedStateOf`, `remember(channels)` re-runs whenever `channels` reference changes even if the group list is identical.

---

## Now Screen Buckets — IO dispatcher (AND-PERF-004)

Before: three full channel-list scans on the composition thread on every `epgReady` change.

After: bucket computation lives in `EpgViewModel`, runs on IO dispatcher, exposed as `StateFlow`:
```kotlin
// EpgViewModel — runs on ioDispatcher
private fun rebuildBuckets() {
    viewModelScope.launch(ioDispatcher) {
        _liveMatches.value  = NowBuckets.liveMatches(channels)  { currentProgramme(it) }
        _liveOnAir.value    = NowBuckets.liveOnAir(channels)    { currentProgramme(it) }
        _startingSoon.value = NowBuckets.startingSoon(channels, ...)
    }
}

// NowScreen — pure collection, zero computation
val liveMatches  by epgViewModel.liveMatches.collectAsState()
val liveOnAir    by epgViewModel.liveOnAir.collectAsState()
val startingSoon by epgViewModel.startingSoon.collectAsState()
```

---

## Favourites — O(1) contains (AND-PERF-006)

`favouriteIds` is `StateFlow<Set<String>>` backed by `stringSetPreferencesKey` (HashSet). All `contains` calls in card rendering are O(1). Never convert to `List` — that degrades to O(n).

---

## M3U Parser — pre-sized list (AND-PERF-009)

```kotlin
// Before — ArrayList resizes repeatedly for 764+ channels
val channels = mutableListOf<Channel>()

// After — pre-sized, eliminates resize/copy cycles
val channels = ArrayList<Channel>(INITIAL_CHANNEL_CAPACITY)  // 512
```

---

## Cold Boot Cache — stale-while-revalidate

The cache layer eliminates the perceived cold boot cost. See `docs/android-architecture.md` for the full warm/cold boot flow.

| Path | Before | After |
|---|---|---|
| Channels visible | 3–8s (network + parse) | ~10ms (cache read) |
| EPG in cards | 5–15s (EPG fetch + parse) | ~500ms (index read) |
| Loading spinner | Every launch | Cold boot only |

Cache files are JSON in `cacheDir` — the OS may evict under storage pressure, which safely triggers a fresh network fetch.

---

## Rules for New Features

Follow these when adding anything that renders per-channel:

1. **No O(n) operations in Compose scope.** Move to ViewModel.
2. **No `System.currentTimeMillis()` per card.** Use `epgViewModel.nowMs`.
3. **No `remember(largeList)` as a key.** Use `derivedStateOf` or move to VM StateFlow.
4. **No EPG reads outside `remember(channel.id, epgReady)`** in card composables.
5. **No `List.contains` for favourites.** Always collect as `Set<String>`.
6. **Search must be debounced.** Call `playlistViewModel.setSearchQuery()` — do not filter in screen.
7. **New filter dimensions belong in `PlaylistViewModel.filteredSections`**, not in a `remember` block in the screen.

---

## Complexity Reference

| Operation | Complexity | Notes |
|---|---|---|
| `currentProgramme(tvgId)` | O(1) | Index map lookup |
| `nextProgramme(tvgId)` | O(1) | Index map lookup |
| `schedule(tvgId, from, to)` | O(k) | k = programmes in range, early exit |
| `filteredSections` recompute | O(n) | IO dispatcher, debounced, not per frame |
| `favouriteIds.contains` | O(1) | HashSet |
| `rebuildIndex()` | O(n total programmes) | Every 30s, IO dispatcher |
| `rebuildBuckets()` | O(n channels) | After index rebuild, IO dispatcher |
| M3U parse | O(n lines) | Single pass, streaming |
| EPG parse | O(n elements) | SAX-style, streaming |
| Cache read (channels) | O(n JSON) | IO dispatcher, warm boot only |
| Cache read (EPG index) | O(k entries) | IO dispatcher, warm boot only |