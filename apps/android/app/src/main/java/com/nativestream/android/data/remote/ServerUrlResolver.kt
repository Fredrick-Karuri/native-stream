// app/src/main/java/com/nativestream/android/data/remote/ServerUrlResolver.kt
//
// Resolves which server URL the app should use, per the precedence order:
//   1. Manual override, if the user set one in Settings
//   2. mDNS-discovered LAN server, if discovery found and health-checked one
//   3. Baked-in hosted default
//

package com.nativestream.android.data.remote

import com.nativestream.android.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

enum class ServerUrlSource { MANUAL_OVERRIDE, LAN_DISCOVERED, HOSTED_DEFAULT }

data class ResolvedServerUrl(
    val url: String,
    val source: ServerUrlSource,
)

object ServerUrlResolver {

    const val HOSTED_DEFAULT_URL = "https://vbccs6bncuo8pdgciav8ra4y.158.158.33.25.sslip.io"

    /**
     * Pure precedence resolution — no I/O, no Android types.
     *
     * @param manualOverride user-entered override from Settings; blank/null means unset
     * @param discoveredLanUrl mDNS-discovered + health-checked LAN server; null means
     *   discovery hasn't found (or hasn't finished validating) one
     */
    fun resolve(
        manualOverride: String?,
        discoveredLanUrl: String?,
    ): ResolvedServerUrl {
        if (!manualOverride.isNullOrBlank()) {
            return ResolvedServerUrl(manualOverride, ServerUrlSource.MANUAL_OVERRIDE)
        }
        if (!discoveredLanUrl.isNullOrBlank()) {
            return ResolvedServerUrl(discoveredLanUrl, ServerUrlSource.LAN_DISCOVERED)
        }
        return ResolvedServerUrl(HOSTED_DEFAULT_URL, ServerUrlSource.HOSTED_DEFAULT)
    }
}

// ── Reactive entry point ─────────────────────────────────────────────────────

@Singleton
class ServerUrlProvider @Inject constructor(
    private val settings: SettingsDataStore,
    private val discovery: ServerDiscoveryService,
) {
    /** Reactive effective server URL — Settings UI and API client both
     *  collect this rather than reading either source independently, so
     *  the precedence rule lives in exactly one place. */
    val effectiveUrl: Flow<ResolvedServerUrl> = combine(
        settings.serverUrl,
        discovery.discoveredUrl,
    ) { manualOverride, discoveredUrl ->
        ServerUrlResolver.resolve(manualOverride, discoveredUrl)
    }
}