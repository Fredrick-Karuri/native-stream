// app/src/main/java/com/nativestream/android/ui/viewmodel/ServerHealthViewModel.kt
//
// Bridges ServerHealthMonitor to the UI layer. Starts the monitor on init,
// exposes reachability and pending discovered URL, and handles confirmation
// by updating settings + triggering reconnects.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.data.remote.ControlSession
import com.nativestream.android.data.remote.ServerHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerHealthViewModel @Inject constructor(
    private val monitor: ServerHealthMonitor,
    private val settingsDataStore: SettingsDataStore,
    private val apiClient: ApiClient,
) : ViewModel() {

    val isReachable: StateFlow<Boolean> = monitor.isReachable
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val pendingUrl: StateFlow<String?> = monitor.pendingUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        monitor.start()
    }

    fun confirmDiscoveredUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setServerUrl(url)
            apiClient.setBaseUrl(url)
            monitor.confirmPendingUrl()
        }
    }

    fun dismissDiscoveredUrl() {
        monitor.dismissPendingUrl()
    }

    override fun onCleared() {
        super.onCleared()
        monitor.stop()
    }
}