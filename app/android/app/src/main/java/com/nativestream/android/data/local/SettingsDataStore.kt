// app/src/main/java/com/nativestream/android/data/local/SettingsDataStore.kt
//
// NS-024: Settings DataStore (stub)
// Interface consumed by PlaylistViewModel and EpgViewModel.
// Full DataStore Preferences implementation lives in AND-024.
// Stub returns safe defaults so ViewModels compile and run before AND-024.

package com.nativestream.android.data.local

import com.nativestream.android.domain.model.PlaylistSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor() {

    // ── Server ────────────────────────────────────────────────────────────────

    private val _serverUrl = MutableStateFlow("http://192.168.1.42:8888")
    val serverUrl: Flow<String> = _serverUrl

    suspend fun setServerUrl(url: String) { _serverUrl.value = url }
    suspend fun serverUrl(): String = _serverUrl.value

    // ── EPG ───────────────────────────────────────────────────────────────────

    private val _epgUrl = MutableStateFlow<String?>(null)
    val epgUrl: Flow<String?> = _epgUrl

    suspend fun setEpgUrl(url: String) { _epgUrl.value = url }
    suspend fun epgUrl(): String? = _epgUrl.value

    // ── Playlist sources ──────────────────────────────────────────────────────

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: Flow<List<PlaylistSource>> = _sources

    suspend fun addSource(source: PlaylistSource) {
        _sources.value = _sources.value + source
    }

    suspend fun removeSource(id: String) {
        _sources.value = _sources.value.filter { it.id != id }
    }

    suspend fun updateSource(source: PlaylistSource) {
        _sources.value = _sources.value.map { if (it.id == source.id) source else it }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private val _bufferPreset = MutableStateFlow(BufferPreset.DEFAULT)
    val bufferPreset: Flow<BufferPreset> = _bufferPreset

    suspend fun setBufferPreset(preset: BufferPreset) { _bufferPreset.value = preset }

    // ── Onboarding ────────────────────────────────────────────────────────────

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: Flow<Boolean> = _onboardingComplete

    suspend fun setOnboardingComplete(complete: Boolean) { _onboardingComplete.value = complete }
    suspend fun isOnboardingComplete(): Boolean = _onboardingComplete.value
}

enum class BufferPreset { LOW, DEFAULT, HIGH }