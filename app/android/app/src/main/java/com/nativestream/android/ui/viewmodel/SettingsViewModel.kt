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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val apiClient: ApiClient,
) : ViewModel() {

    val serverUrl: StateFlow<String> = settingsDataStore.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "http://192.168.1.42:8888")

    val epgUrl: StateFlow<String?> = settingsDataStore.epgUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bufferPreset: StateFlow<BufferPreset> = settingsDataStore.bufferPreset
        .stateIn(viewModelScope, SharingStarted.Eagerly, BufferPreset.DEFAULT)

    val onboardingComplete: StateFlow<Boolean> = settingsDataStore.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(url)
            apiClient.setBaseUrl(url)
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
}