# NativeStream Android — Cold Boot Cache Tickets

**Platform:** Android 8.0+ (API 26+)  
**Stack:** Ktor disk cache · kotlinx.serialization · DataStore  
**Feature:** Stale-while-revalidate cache for M3U channels and EPG data  
**Last Updated:** 2026-06-13

---

## How to Read This

- **ID** — `AND-CACHE-001` etc.
- **Effort** — S (< 2hrs), M (half day), L (full day)
- **Done when** — observable acceptance criteria

---

## Strategy

No Room. No new dependencies. Use:
- **`cacheDir` JSON files** via kotlinx.serialization for channels and EPG index
- **Stale-while-revalidate** — load cache instantly on boot, fetch fresh in background, update UI when ready
- **Ktor's existing `HttpCache`** already caches raw bytes — we layer a parsed-data cache on top so parsing is also skipped on warm boot

Cold boot flow after this epic:
```
App opens
  → load channels from cache file        (~10ms, instant grid)
  → load EPG index from cache file       (~20ms, instant EPG in cards)
  → show UI immediately
  → fetch fresh M3U in background        (network)
  → fetch fresh EPG in background        (network)
  → update UI when fresh data arrives
```

---

## EPIC AND-CACHE-E01 — Channel Cache

---

### AND-CACHE-001 — ChannelCache: persist parsed channels to disk
- **Effort:** M
- **Needs:** `data/parser/M3uParser.kt`, `domain/model/Channel.kt`, `data/remote/ApiClient.kt`
- **Description:** Create `data/local/ChannelCache.kt` — a singleton that reads/writes `List<Channel>` as JSON to `cacheDir/channels_{sourceId}.json`. Uses `kotlinx.serialization` (already a dependency). Write happens after each successful M3U parse in `PlaylistViewModel.fetchAllSourcesInParallel`. Read happens in `PlaylistViewModel.init` before the network fetch — emits cached channels immediately so the grid populates on first frame. Cache is keyed per `source.id` so multi-source setups cache independently.
- **API:**
  ```kotlin
  suspend fun write(sourceId: String, channels: List<Channel>)
  suspend fun read(sourceId: String): List<Channel>?
  suspend fun clear(sourceId: String)
  ```
- **Done when:** On cold boot with no network, grid populates from cache within 1 second. After network fetch completes, grid updates with fresh data. Cache file exists in `cacheDir` after first successful load.

---

### AND-CACHE-002 — PlaylistViewModel: load cache before network fetch
- **Effort:** M
- **Needs:** `ui/viewmodel/PlaylistViewModel.kt`, AND-CACHE-001
- **Description:** In `PlaylistViewModel.init`, before the `settingsDataStore.sources.collect` triggers `loadAll()`, attempt to load cached channels for each known source. Emit cached channels to `_channels` immediately. Set a `_isCacheLoaded` flag. When `loadAll()` completes, overwrite with fresh data and write new cache. Loading indicator (`isLoading`) should only show on first boot when cache is empty — on subsequent boots show stale data immediately with a subtle background-refresh indicator.
- **Done when:** Second app launch shows channels before any network request completes. `isLoading` is false on warm boot until fresh data differs from cache.

---

### AND-CACHE-003 — Cache invalidation: TTL and source change
- **Effort:** S
- **Needs:** `data/local/ChannelCache.kt`, AND-CACHE-001
- **Description:** Cache entries should be invalidated when: (a) source URL changes, (b) cache file is older than `source.refreshIntervalHours`, (c) `removeSource()` is called. Store a metadata sidecar `channels_{sourceId}.meta.json` with `{ "cachedAt": epochMs, "sourceUrl": "..." }`. On read, check TTL and URL match — stale or mismatched cache is ignored and triggers a fresh fetch. `removeSource()` deletes both cache files.
- **Done when:** Changing a source URL invalidates its cache. Cache older than `refreshIntervalHours` is not used. Deleted sources clean up cache files.

---

## EPIC AND-CACHE-E02 — EPG Cache

---

### AND-CACHE-004 — EpgIndexCache: persist current/next programme index to disk
- **Effort:** M
- **Needs:** `data/parser/EpgStore.kt`, `ui/viewmodel/EpgViewModel.kt`, AND-PERF-001
- **Description:** After AND-PERF-001 builds a `Map<tvgId, Programme?>` current index, persist it to `cacheDir/epg_index_{sourceId}.json` via kotlinx.serialization. On cold boot, `EpgViewModel.init` reads the index file before fetching fresh EPG — populates `_isReady = true` immediately so cards show EPG data on first frame. Full `EpgStore` (44k programmes) is NOT cached — only the precomputed current + next index maps, which are small (~764 entries × 2). Full EPG is re-fetched and re-parsed in background.
- **API:**
  ```kotlin
  suspend fun writeIndex(sourceId: String, current: Map<String, Programme?>, next: Map<String, Programme?>)
  suspend fun readIndex(sourceId: String): EpgIndexSnapshot?
  ```
- **Done when:** On warm boot, channel cards show EPG programme titles before EPG network fetch completes. `_isReady` emits `true` within 500ms of app open.

---

### AND-CACHE-005 — EpgViewModel: load index cache before network fetch
- **Effort:** M
- **Needs:** `ui/viewmodel/EpgViewModel.kt`, AND-CACHE-004
- **Description:** In `EpgViewModel.init`, before `load()` fetches fresh EPG, read cached index from `EpgIndexCache`. If present and less than 2 hours old, populate the in-memory current/next maps immediately and set `_isReady = true`. Then proceed with background EPG fetch — when complete, rebuild index from fresh data, overwrite cache, notify observers. The background fetch should not set `_isLoading = true` on warm boot (use a separate `_isRefreshing` flag) so the UI does not show a loading state over stale-but-valid data.
- **Done when:** Warm boot: EPG visible in cards within 500ms, no loading spinner. Fresh EPG silently replaces stale after network fetch. Cold boot (no cache): loading spinner shows until first EPG fetch completes.

---

### AND-CACHE-006 — EpgIndexCache TTL and invalidation
- **Effort:** S
- **Needs:** `data/local/EpgIndexCache.kt`, AND-CACHE-004
- **Description:** EPG index cache expires after 2 hours (matching the `max-age=7200` in `ApiClient`). Store `cachedAt` in the index file. On read, if `System.currentTimeMillis() - cachedAt > 2 * MS_PER_HOUR`, discard and return null — triggers a fresh fetch with loading spinner. On source removal, delete associated index cache file.
- **Done when:** Cache older than 2 hours is not used. Stale index does not show wrong programme data. Source removal cleans up index file.

---

## EPIC AND-CACHE-E03 — UX

---

### AND-CACHE-007 — Background refresh indicator
- **Effort:** S
- **Needs:** `ui/viewmodel/PlaylistViewModel.kt`, `ui/viewmodel/EpgViewModel.kt`, `ui/screens/browse/BrowseScreen.kt`, `ui/screens/now/NowScreen.kt`
- **Description:** Add `_isRefreshing: StateFlow<Boolean>` to both VMs — true only during background network refresh on warm boot, distinct from `_isLoading` (cold boot). In `BrowseTopBar` and `NowTopBar`, show a small `CircularProgressIndicator` (12dp, `NSColors.text3`) next to the title when `isRefreshing` is true. Disappears silently when refresh completes. No full-screen loading overlay on warm boot.
- **Done when:** Warm boot shows a subtle spinner in the top bar while background refresh runs. No channels disappear during refresh. Spinner disappears when fresh data arrives.

---

## Dependency Order

```
AND-CACHE-001   ChannelCache write/read         ← foundation
AND-CACHE-002   PlaylistViewModel warm boot      ← needs 001
AND-CACHE-003   Channel cache invalidation       ← needs 001
AND-CACHE-004   EpgIndexCache write/read         ← needs AND-PERF-001
AND-CACHE-005   EpgViewModel warm boot           ← needs 004
AND-CACHE-006   EPG cache invalidation           ← needs 004
AND-CACHE-007   Background refresh indicator     ← needs 002, 005
```

---

## Summary

| Epic | Tickets | Effort |
|---|---|---|
| AND-CACHE-E01 Channel cache | 001–003 | ~1 day |
| AND-CACHE-E02 EPG cache | 004–006 | ~1 day |
| AND-CACHE-E03 UX | 007 | ~0.5 days |
| **Total** | **7 tickets** | **~2.5 days** |

---

## Expected Cold Boot Gains

| Metric | Before | After |
|---|---|---|
| Channels visible | ~3–8s (network + parse) | ~10ms (cache read) |
| EPG in cards | ~5–15s (EPG fetch + parse) | ~500ms (index read) |
| Loading spinner shown | Always on every boot | Cold boot only |
| Network requests blocked on | UI render | Background only |

---

## Notes

- No Room, no new dependencies — `kotlinx.serialization` + `cacheDir` files only
- `Channel` is already `@Serializable` — cache write is one `Json.encodeToString` call
- `Programme` is already `@Serializable` — index serialization is free
- Ktor's `HttpCache` already caches raw bytes — this layer caches **parsed** data, skipping parse time on warm boot
- Cache files live in `cacheDir` — OS may evict under storage pressure, which is safe (triggers fresh fetch)