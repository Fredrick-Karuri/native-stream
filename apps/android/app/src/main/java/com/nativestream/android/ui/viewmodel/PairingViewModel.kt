// app/src/main/java/com/nativestream/android/ui/viewmodel/PairingViewModel.kt
//
// Drives the device-pairing handshake: starts a session, shows
// the code, polls until approved/expired, and on approval persists the
// token via SecureTokenStore and pushes it into ApiClient.

package com.nativestream.android.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.local.SecureTokenStore
import com.nativestream.android.data.remote.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 2_000L

sealed interface PairingState {
    data object Idle : PairingState
    data object Starting : PairingState
    data class WaitingForApproval(val code: String) : PairingState
    data object Approved : PairingState
    data object Expired : PairingState
    data object Denied : PairingState
    data class Failed(val message: String) : PairingState
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val tokenStore: SecureTokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var currentSessionId: String? = null

    /**
     * Starts a new pairing session and begins polling. Safe to call again
     * after expiry — cancels any in-flight poll loop first.
     */
    fun start() {
        stop()
        _state.value = PairingState.Starting

        pollJob = viewModelScope.launch {
            runPairingFlow()
        }
    }

    /**
     * Cancels any in-flight pairing session poll. Call from the screen's
     * DisposableEffect onDispose, and internally before starting fresh.
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private suspend fun runPairingFlow() {
        val session = try {
            apiClient.startPairing(platform = devicePlatformLabel())
        } catch (cause: Exception) {
            _state.value = PairingState.Failed("Could not start pairing: ${cause.message}")
            return
        }

        currentSessionId = session.sessionId
        _state.value = PairingState.WaitingForApproval(session.code)

        pollUntilResolved(session.sessionId)
    }

    private suspend fun pollUntilResolved(sessionId: String) {
        while (viewModelScope.isActive) {
            try {
                val status = apiClient.pairingStatus(sessionId)
                when (status.status) {
                    "approved" -> {
                        tokenStore.setApiToken(status.token)
                        apiClient.setApiToken(status.token)
                        _state.value = PairingState.Approved
                        return
                    }
                    "expired" -> {
                        _state.value = PairingState.Expired
                        return
                    }
                    "denied" -> {
                        _state.value = PairingState.Denied
                        return
                    }
                    else -> {
                        // still pending — keep polling
                    }
                }
            } catch (cause: Exception) {
                // A transient network error while polling shouldn't kill
                // the whole flow — keep polling until expiry catches it,
                // rather than surfacing a scary error for one dropped
                // request.
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun devicePlatformLabel(): String =
        "Android (${Build.MANUFACTURER} ${Build.MODEL})"
}