// app/src/main/java/com/nativestream/android/ui/viewmodel/EpgViewModel.kt
//
// EPG ViewModel
// Owns EPG loading state and all programme query methods.
// Parallel source loading, gzip support,
// GitHub raw URL normalisation, sport helpers, match-rate diagnostic.

package com.nativestream.android.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.EpgIndexCache
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.parser.EpgParser
import com.nativestream.android.data.parser.EpgStore
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.domain.model.Channel
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.domain.model.SportCategory
import com.nativestream.android.domain.repository.ChannelRepository
import com.nativestream.android.ui.screens.now.ChannelWithProgramme
import com.nativestream.android.ui.screens.now.NowBuckets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Named


private const val TAG = "EpgViewModel"
private const val DEFAULT_SCHEDULE_HOURS = 6
private const val MS_PER_HOUR = 3_600_000L
private const val INDEX_REBUILD_INTERVAL_MS = 30_000L
private const val GITHUB_HOST        = "github.com"
private const val GITHUB_PREFIX      = "https://github.com/"
private const val GITHUB_RAW_PREFIX  = "https://raw.githubusercontent.com/"

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val epgParser: EpgParser,
    private val settingsDataStore: SettingsDataStore,
    private val epgIndexCache: EpgIndexCache,
    private val channelRepository: ChannelRepository,
    @Named("io") private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {


    // ── Public state ──────────────────────────────────────────────────────────

    private val _isLoading   = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Keyed by source id — mirrors Swift stores: [UUID: EPGStore]
    private val stores = mutableMapOf<String, EpgStore>()
    private var indexRebuildJob: Job? = null

    private val _nowMs = MutableStateFlow(System.currentTimeMillis())
    val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

    private val _channels = MutableStateFlow<List<com.nativestream.android.domain.model.Channel>>(emptyList())

    // public bucket flows:
    private val _liveMatches  = MutableStateFlow<List<ChannelWithProgramme>>(emptyList())
    private val _liveOnAir    = MutableStateFlow<List<ChannelWithProgramme>>(emptyList())
    private val _startingSoon = MutableStateFlow<List<ChannelWithProgramme>>(emptyList())

    val liveMatches:  StateFlow<List<ChannelWithProgramme>> = _liveMatches.asStateFlow()
    val liveOnAir:    StateFlow<List<ChannelWithProgramme>> = _liveOnAir.asStateFlow()
    val startingSoon: StateFlow<List<ChannelWithProgramme>> = _startingSoon.asStateFlow()


    // ── Load ──────────────────────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            loadFromCacheThenRefresh()
        }
        startIndexRebuildTimer()
        Log.d(TAG, "EpgViewModel init, repo=${channelRepository.hashCode()}")
        viewModelScope.launch {
            channelRepository.channels.collect { channels ->
                Log.d(TAG, "channelRepository emitted: ${channels.size}")
                updateChannels(channels)
            }
        }
    }

    private suspend fun loadFromCacheThenRefresh() {
        val sources = settingsDataStore.sources.first()
        if (sources.isEmpty()) {
            load()
            return
        }

        // Try to load index from cache for each source
        val snapshots = withContext(ioDispatcher) {
            sources.mapNotNull { source ->
                epgIndexCache.readIndex(source.id)
            }
        }

        if (snapshots.isNotEmpty()) {
            // Warm boot — populate index immediately from cache
            val mergedCurrent = mutableMapOf<String, Programme?>()
            val mergedNext    = mutableMapOf<String, Programme?>()
            snapshots.forEach { snapshot ->
                mergedCurrent.putAll(snapshot.currentIndex)
                mergedNext.putAll(snapshot.nextIndex)
            }

            // Inject into a synthetic store so currentProgramme/nextProgramme work immediately
            stores["__cache__"] = EpgStore.fromIndex(mergedCurrent, mergedNext)
            _isReady.value = true
            rebuildBuckets()

            // Background refresh — no loading spinner
            _isRefreshing.value = true
            load(isBackgroundRefresh = true)
        } else {
            // Cold boot — show spinner
            load(isBackgroundRefresh = false)
        }
    }

    private fun startIndexRebuildTimer() {
        indexRebuildJob?.cancel()
        indexRebuildJob = viewModelScope.launch(ioDispatcher) {
            while (true) {
                delay(INDEX_REBUILD_INTERVAL_MS)
                val nowMs = System.currentTimeMillis()
                _nowMs.value = nowMs
                stores.values.forEach { it.rebuildIndex(nowMs) }
                rebuildBuckets()
            }
        }
    }

    fun clearSourceCache(sourceId: String) {
        viewModelScope.launch {
            epgIndexCache.clear(sourceId)
            stores.remove(sourceId)
        }
    }

    @androidx.annotation.VisibleForTesting
    fun cancelBackgroundWorkForTest() {
        indexRebuildJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        indexRebuildJob?.cancel()
    }

    fun load(isBackgroundRefresh: Boolean = false) {
        if (_isLoading.value) return
        if (isBackgroundRefresh) {
            _isRefreshing.value = true
        } else {
            _isLoading.value = true
        }

        viewModelScope.launch {
            try {
                val sources = settingsDataStore.sources.first()
                val newStores = withContext(ioDispatcher) {
                    fetchAllStoresInParallel(sources)
                }
                stores.clear()
                stores.putAll(newStores)
                _isAvailable.value = stores.isNotEmpty()
                _isReady.value = true
                logLoadSummary()

                // Rebuild index and persist to cache
                val nowMs = System.currentTimeMillis()
                stores.forEach { (sourceId, store) ->
                    store.rebuildIndex(nowMs)
                    epgIndexCache.writeIndex(
                        sourceId     = sourceId,
                        currentIndex = store.currentIndexSnapshot(),
                        nextIndex    = store.nextIndexSnapshot(),
                    )
                }
                _nowMs.value = nowMs
                rebuildBuckets()
            } catch (e: Exception) {
                Log.e(TAG, "EPG load failed", e)
                _isAvailable.value = false
            } finally {
                _isLoading.value    = false
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetchAllStoresInParallel(
        sources: List<com.nativestream.android.domain.model.PlaylistSource>,
    ): Map<String, EpgStore> = coroutineScope {
        val urls = buildList {
            settingsDataStore.epgUrl()?.let { add("settings" to it) }
            sources.forEach { source ->
                source.epgUrl?.let { add(source.id to it) }
            }
        }
        urls.map { (key, url) ->
            async {
                try { key to fetchAndParse(url) }
                catch (e: Exception) {
                    Log.w(TAG, "Failed EPG $url: ${e.message}")
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private suspend fun fetchAndParse(rawUrl: String): EpgStore {
        val url = normalizeEpgUrl(rawUrl)
        val bytes = apiClient.fetchRawUrl(url)
        val decompressed = if (url.endsWith(".gz")) decompressGzip(bytes) else bytes
        return epgParser.parse(ByteArrayInputStream(decompressed))
    }

    // ── Gzip decompression ────────────────────────────────────────────────────

    private fun decompressGzip(compressed: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }

    // ── URL normalisation — mirrors EPGViewModel.normalizeEPGURL ─────────────

    private fun normalizeEpgUrl(url: String): String {
        if (!url.contains(GITHUB_HOST)) return url
        return url
            .replace(GITHUB_PREFIX, GITHUB_RAW_PREFIX)
            .replace("/raw/", "/")
    }

    // ── Programme queries ─────────────────────────────────────────────────────

    fun currentProgramme(channel: Channel): Programme? =
        stores.values.firstNotNullOfOrNull { it.currentProgramme(channel.tvgId) }

    fun nextProgramme(channel: Channel): Programme? =
        stores.values.firstNotNullOfOrNull { it.nextProgramme(channel.tvgId) }

    fun schedule(
        channel: Channel,
        hours: Int = DEFAULT_SCHEDULE_HOURS,
    ): List<Programme> {
        val nowMs    = System.currentTimeMillis()
        val cutoffMs = nowMs + (hours * MS_PER_HOUR)
        val seen     = mutableSetOf<String>()
        return stores.values
            .flatMap { it.schedule(channel.tvgId, nowMs, cutoffMs) }
            .filter { it.stopEpochMs > nowMs && it.startEpochMs < cutoffMs }
            .filter { seen.add(it.id) }
            .sortedBy { it.startEpochMs }
    }

    fun schedule(channel: Channel, fromEpochMs: Long, toEpochMs: Long): List<Programme> =
        stores.values
            .flatMap { it.schedule(channel.tvgId, fromEpochMs, toEpochMs) }
            .sortedBy { it.startEpochMs }

    // ── Diagnostic (AND-026) ──────────────────────────────────────────────────

    fun logMatchDiagnostic(channels: List<Channel>) {
        if (channels.isEmpty()) return
        val matchedCount = channels.count { channel ->
            stores.values.any { it.currentProgramme(channel.tvgId) != null }
        }
        val matchPercent = (matchedCount.toDouble() / channels.size * 100).toInt()
        Log.i(TAG, "EPG match rate: $matchPercent% ($matchedCount/${channels.size})")

        channels.filter { channel ->
            stores.values.none { it.programmesFor(channel.tvgId).isNotEmpty() }
        }.forEach { unmatched ->
            Log.d(TAG, "Unmatched channel: '${unmatched.name}' tvgId='${unmatched.tvgId}'")
        }
    }

    // ── Sport helpers (AND-027) ───────────────────────────────────────────────

    fun hasContent(sport: SportCategory, channels: List<Channel>): Boolean =
        liveCount(sport, channels) > 0 || upcomingCount(sport, channels) > 0

    fun liveCount(sport: SportCategory, channels: List<Channel>): Int =
        channels.count { channel ->
            currentProgramme(channel)?.let { matchesSport(sport, it) } == true
        }

    fun upcomingCount(sport: SportCategory, channels: List<Channel>): Int =
        channels.count { channel ->
            nextProgramme(channel)?.let { matchesSport(sport, it) } == true
        }

    fun activeSports(channels: List<Channel>): List<SportCategory> =
        SportCategory.entries
            .filter { hasContent(it, channels) }
            .sortedByDescending { liveCount(it, channels) }

    fun matchesSport(sport: SportCategory, programme: Programme): Boolean {
        val lowercaseTitle = programme.title.lowercase()
        return sport.epgKeywords.any { keyword -> lowercaseTitle.contains(keyword) }
    }

    fun updateChannels(channels: List<com.nativestream.android.domain.model.Channel>) {
        _channels.value = channels
        rebuildBuckets()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun logLoadSummary() {
        val totalProgrammes = stores.values.sumOf { it.programmeCount }
        val totalChannels   = stores.values.sumOf { it.channelCount }
        Log.i(TAG, "EPG loaded: $totalProgrammes programmes, $totalChannels channels across ${stores.size} store(s)")
    }
    private fun rebuildBuckets() {
        viewModelScope.launch(ioDispatcher) {
            val channels = _channels.value
            if (channels.isEmpty() || !_isReady.value) return@launch

            _liveMatches.value  = NowBuckets.liveMatches(channels)  { currentProgramme(it) }
            _liveOnAir.value    = NowBuckets.liveOnAir(channels)    { currentProgramme(it) }
            _startingSoon.value = NowBuckets.startingSoon(
                channels            = channels,
                currentProgrammeFor = { currentProgramme(it) },
                nextProgrammeFor    = { nextProgramme(it) },
            )
        }
    }
}