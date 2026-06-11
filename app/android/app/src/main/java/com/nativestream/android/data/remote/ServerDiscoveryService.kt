// app/src/main/java/com/nativestream/android/data/remote/ServerDiscoveryService.kt

package com.nativestream.android.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ServerDiscovery"
private const val SERVICE_TYPE = "_nativestream._tcp"
private const val HEALTH_TIMEOUT_MS = 2_000L

@Singleton
class ServerDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _discoveredUrl = MutableStateFlow<String?>(null)
    val discoveredUrl: StateFlow<String?> = _discoveredUrl

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var listener: NsdManager.DiscoveryListener? = null

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        _discoveredUrl.value = null

        listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.w(TAG, "Start failed: $code"); _scanning.value = false
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {
                Log.w(TAG, "Stop failed: $code")
            }
            override fun onDiscoveryStarted(type: String) {
                Log.d(TAG, "Scanning for $type")
            }
            override fun onDiscoveryStopped(type: String) {
                _scanning.value = false
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                nsdManager.resolveService(info, makeResolveListener())
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                Log.d(TAG, "Lost: ${info.serviceName}")
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        listener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        listener = null
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
            Log.w(TAG, "Resolve failed: $code")
        }
        override fun onServiceResolved(info: NsdServiceInfo) {
            val url = "http://${info.host.hostAddress}:${info.port}"
            Log.d(TAG, "Resolved: $url")
            scope.launch {
                val reachable = runCatching {
                    withTimeout(HEALTH_TIMEOUT_MS) {
                        apiClient.fetchRawUrl("$url/api/health")
                        true
                    }
                }.getOrDefault(false)
                if (reachable) {
                    _discoveredUrl.value = url
                    stop()
                }
            }
        }
    }
}