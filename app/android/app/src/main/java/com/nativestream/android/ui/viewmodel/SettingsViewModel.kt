// app/src/main/java/com/nativestream/android/ui/viewmodel/SettingsViewModel.kt
//
// Settings ViewModel
// Bridges SettingsDataStore to the settings screen. Triggers EPG + playlist
// reload when server URL changes.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.BufferPreset
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.remote.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nativestream.android.data.remote.ServerDiscoveryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val apiClient: ApiClient,
    private val discoveryService: ServerDiscoveryService,
) : ViewModel() {

    init {
        viewModelScope.launch {
            settingsDataStore.serverUrl.first().let { apiClient.setBaseUrl(it) }
        }
    }

    val serverUrl: StateFlow<String> = settingsDataStore.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "http://192.168.1.42:8888")

    val epgUrl: StateFlow<String?> = settingsDataStore.epgUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bufferPreset: StateFlow<BufferPreset> = settingsDataStore.bufferPreset
        .stateIn(viewModelScope, SharingStarted.Eagerly, BufferPreset.DEFAULT)

    val onboardingComplete: StateFlow<Boolean> = settingsDataStore.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isLoading: StateFlow<Boolean> = settingsDataStore.onboardingComplete
        .map { false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val discoveredUrl: StateFlow<String?> = discoveryService.discoveredUrl
    val scanning: StateFlow<Boolean> = discoveryService.scanning

    private val _serverReachable = MutableStateFlow<Boolean>(true)
    val serverReachable: StateFlow<Boolean> = _serverReachable

    fun startDiscovery() = discoveryService.scan()

    fun confirmDiscoveredUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(url)
            apiClient.setBaseUrl(url)
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(url)
            apiClient.setBaseUrl(url)
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            _serverReachable.value = runCatching {
                withTimeout(5_000) { apiClient.health(); true }
            }.getOrDefault(false)
        }
    }


    fun setEpgUrl(url: String) {
        viewModelScope.launch { settingsDataStore.setEpgUrl(url) }
    }

    fun setBufferPreset(preset: BufferPreset) {
        viewModelScope.launch { settingsDataStore.setBufferPreset(preset) }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch { settingsDataStore.setOnboardingComplete(complete) }
    }

    fun triggerProbe(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = runCatching { apiClient.triggerProbe() }.isSuccess
            onResult(success)
        }
    }
}