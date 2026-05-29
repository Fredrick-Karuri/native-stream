package com.nativestream.android.data.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Chromecast options stub.
 * Full implementation in AND-021 (Chromecast).
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_MEDIA_RECEIVER_APP_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()

    companion object {
        // Google's built-in default media receiver — swap for custom receiver in AND-021
        private const val DEFAULT_MEDIA_RECEIVER_APP_ID = "CC1AD845"
    }
}