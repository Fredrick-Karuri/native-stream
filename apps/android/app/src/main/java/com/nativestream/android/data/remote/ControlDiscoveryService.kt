// app/src/main/java/com/nativestream/android/data/remote/ControlDiscoveryService.kt
//
// Scans the local network for _nativestream-ctrl._tcp via NsdManager,
// extracts the WebSocket URL from the resolved host/port and TXT record,
// and emits a confirmed ws:// URL via StateFlow.
// Mirrors ServerDiscoveryService but targets the control service type.

package com.nativestream.android.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG                  = "ControlDiscovery"
private const val CONTROL_SERVICE_TYPE = "_nativestream-ctrl._tcp"
private const val DEFAULT_WS_PATH      = "/ws"
private const val TXT_KEY_WS_PATH      = "ws"

@Singleton
class ControlDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _controlServerUrl = MutableStateFlow<String?>(null)
    val controlServerUrl: StateFlow<String?> = _controlServerUrl

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var listener: NsdManager.DiscoveryListener? = null

    fun scan() {
        if (_scanning.value) return
        _scanning.value       = true
        _controlServerUrl.value = null

        listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.w(TAG, "Start failed: $code")
                _scanning.value = false
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

        nsdManager.discoverServices(
            CONTROL_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener,
        )
    }

    fun stop() {
        listener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        listener        = null
        _scanning.value = false
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
            Log.w(TAG, "Resolve failed: $code")
        }

        override fun onServiceResolved(info: NsdServiceInfo) {
            val host    = info.host?.hostAddress ?: return
            val port    = info.port
            val wsPath  = extractWsPath(info) ?: DEFAULT_WS_PATH
            val wsUrl   = "ws://$host:$port$wsPath"

            Log.d(TAG, "Control service resolved: $wsUrl")
            _controlServerUrl.value = wsUrl
            stop()
        }
    }

    // Extract ws path from TXT record attributes if available
    private fun extractWsPath(info: NsdServiceInfo): String? {
        return runCatching {
            // NsdServiceInfo.attributes is Map<String, ByteArray> on API 21+
            val attrs = info.attributes
            attrs[TXT_KEY_WS_PATH]?.toString(Charsets.UTF_8)
        }.getOrNull()
    }
}