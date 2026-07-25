// app/src/main/java/com/nativestream/android/data/remote/ServerHealthMonitor.kt
//
// Polls server health on a fixed interval. On consecutive failures triggers
// mDNS re-discovery automatically. Exposes reachability state and any
// newly discovered URL pending user confirmation.

package com.nativestream.android.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG                    = "ServerHealthMonitor"
private const val POLL_INTERVAL_MS       = 30_000L
private const val HEALTH_TIMEOUT_MS      = 5_000L
private const val FAILURES_BEFORE_SCAN   = 2  // 2 consecutive failures → trigger discovery

@Singleton
class ServerHealthMonitor @Inject constructor(
    private val apiClient: ApiClient,
    private val discoveryService: ServerDiscoveryService,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isReachable = MutableStateFlow(true)
    val isReachable: StateFlow<Boolean> = _isReachable.asStateFlow()

    // A newly discovered URL awaiting user confirmation — null when none pending
    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    private var pollJob: Job? = null
    private var failureCount  = 0

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            // Observe discovery results and surface them as pending
            launch {
                discoveryService.discoveredUrl.collect { url ->
                    if (url != null) {
                        Log.d(TAG, "New server discovered: $url")
                        _pendingUrl.value = url
                    }
                }
            }

            // Health poll loop
            while (true) {
                delay(POLL_INTERVAL_MS)
                val reachable = checkHealth()
                _isReachable.value = reachable

                if (!reachable) {
                    failureCount++
                    Log.w(TAG, "Health check failed ($failureCount consecutive)")
                    if (failureCount >= FAILURES_BEFORE_SCAN && !discoveryService.scanning.value) {
                        Log.d(TAG, "Triggering auto-discovery after $failureCount failures")
                        discoveryService.scan()
                    }
                } else {
                    failureCount = 0
                }
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    // Called by UI when user confirms a pending discovered URL
    fun confirmPendingUrl() {
        _pendingUrl.value = null
        failureCount      = 0
        _isReachable.value = true
    }

    // Called by UI when user dismisses the banner without confirming
    fun dismissPendingUrl() {
        _pendingUrl.value = null
    }

    private suspend fun checkHealth(): Boolean =
        runCatching {
            withTimeout(HEALTH_TIMEOUT_MS) {
                apiClient.health()
                true
            }
        }.getOrDefault(false)
}