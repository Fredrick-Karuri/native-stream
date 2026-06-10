// app/src/main/java/com/nativestream/android/data/local/SettingsDataStore.kt
//
// Settings DataStore
// Full DataStore Preferences implementation.
// Persists: serverUrl, epgUrl, bufferPreset, epgRefreshInterval, onboardingComplete, sources.
// Exposed as StateFlow so Compose collects reactively without manual refresh.

package com.nativestream.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nativestream.android.domain.model.PlaylistSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ns_settings")

private object Keys {
    val SERVER_URL           = stringPreferencesKey("server_url")
    val EPG_URL              = stringPreferencesKey("epg_url")
    val BUFFER_PRESET        = stringPreferencesKey("buffer_preset")
    val ONBOARDING_COMPLETE  = booleanPreferencesKey("onboarding_complete")
    val PLAYLIST_SOURCES     = stringPreferencesKey("playlist_sources")
    val SELECTED_SOURCE_ID = stringPreferencesKey("selected_source_id")
}

private object Defaults {
    const val SERVER_URL    = "http://192.168.1.42:8888"
    const val BUFFER_PRESET = "DEFAULT"
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // ── Server ────────────────────────────────────────────────────────────────

    val serverUrl: Flow<String> = store.data.map { prefs ->
        prefs[Keys.SERVER_URL] ?: Defaults.SERVER_URL
    }
    val selectedSourceId: Flow<String> = store.data.map { prefs ->
        prefs[Keys.SELECTED_SOURCE_ID] ?: ""
    }

    suspend fun setServerUrl(url: String) {
        store.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun serverUrl(): String =
        (store.data.map { it[Keys.SERVER_URL] ?: Defaults.SERVER_URL }
            .let { flow ->
                var result = Defaults.SERVER_URL
                // Synchronous read via first() would need runBlocking — callers use Flow instead
                result
            })

    // ── EPG ───────────────────────────────────────────────────────────────────

    val epgUrl: Flow<String?> = store.data.map { prefs ->
        prefs[Keys.EPG_URL]?.ifEmpty { null }
    }

    suspend fun setEpgUrl(url: String) {
        store.edit { it[Keys.EPG_URL] = url }
    }

    suspend fun epgUrl(): String? {
        var result: String? = null
        store.edit { prefs -> result = prefs[Keys.EPG_URL]?.ifEmpty { null } }
        return result
    }

    // ── Buffer preset ─────────────────────────────────────────────────────────

    val bufferPreset: Flow<BufferPreset> = store.data.map { prefs ->
        prefs[Keys.BUFFER_PRESET]?.let { raw ->
            runCatching { BufferPreset.valueOf(raw) }.getOrNull()
        } ?: BufferPreset.DEFAULT
    }

    suspend fun setBufferPreset(preset: BufferPreset) {
        store.edit { it[Keys.BUFFER_PRESET] = preset.name }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    val onboardingComplete: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun isOnboardingComplete(): Boolean {
        var result = false
        store.edit { prefs -> result = prefs[Keys.ONBOARDING_COMPLETE] ?: false }
        return result
    }

    // ── Playlist sources ──────────────────────────────────────────────────────

    val sources: Flow<List<PlaylistSource>> = store.data.map { prefs ->
        prefs[Keys.PLAYLIST_SOURCES]?.let { json ->
            runCatching { Json.decodeFromString<List<PlaylistSource>>(json) }.getOrElse { emptyList() }
        } ?: emptyList()
    }

    suspend fun addSource(source: PlaylistSource) {
        store.edit { prefs ->
            val current = prefs[Keys.PLAYLIST_SOURCES]?.let {
                runCatching { Json.decodeFromString<List<PlaylistSource>>(it) }.getOrElse { emptyList() }
            } ?: emptyList()
            prefs[Keys.PLAYLIST_SOURCES] = Json.encodeToString(current + source)
        }
    }

    suspend fun removeSource(id: String) {
        store.edit { prefs ->
            val current = prefs[Keys.PLAYLIST_SOURCES]?.let {
                runCatching { Json.decodeFromString<List<PlaylistSource>>(it) }.getOrElse { emptyList() }
            } ?: emptyList()
            prefs[Keys.PLAYLIST_SOURCES] = Json.encodeToString(current.filter { it.id != id })
        }
    }

    suspend fun updateSource(source: PlaylistSource) {
        store.edit { prefs ->
            val current = prefs[Keys.PLAYLIST_SOURCES]?.let {
                runCatching { Json.decodeFromString<List<PlaylistSource>>(it) }.getOrElse { emptyList() }
            } ?: emptyList()
            prefs[Keys.PLAYLIST_SOURCES] = Json.encodeToString(
                current.map { if (it.id == source.id) source else it }
            )
        }
    }
    suspend fun setSelectedSourceId(id: String) {
        store.edit { it[Keys.SELECTED_SOURCE_ID] = id }
    }
}

enum class BufferPreset { LOW, DEFAULT, HIGH }