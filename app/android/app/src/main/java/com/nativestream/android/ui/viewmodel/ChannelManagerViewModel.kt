// app/src/main/java/com/nativestream/android/ui/viewmodel/ChannelManagerViewModel.kt
//
// Handles channel creation, update, and deletion via ApiClient.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.data.remote.CreateChannelRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_GROUP = "General"

@HiltViewModel
class ChannelManagerViewModel @Inject constructor(
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Creates a channel on the server. Mirrors channelManager.addChannel() in Swift.
     * Clears error on start; sets error on failure.
     */
    fun addChannel(
        name: String,
        groupTitle: String,
        tvgId: String,
        logoUrl: String,
        streamUrl: String,
        keywords: List<String>,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                apiClient.createChannel(
                    CreateChannelRequest(
                        name       = name,
                        groupTitle = groupTitle.ifEmpty { DEFAULT_GROUP },
                        tvgId      = tvgId,
                        logoUrl    = logoUrl,
                        streamUrl  = streamUrl,
                        keywords   = keywords,
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add channel"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}