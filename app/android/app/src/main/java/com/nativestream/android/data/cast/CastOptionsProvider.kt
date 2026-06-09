// app/src/main/java/com/nativestream/android/data/cast/CastOptionsProvider.kt
//
// Chromecast Options Provider
// Configures the Cast SDK with the default media receiver.
// Swap DEFAULT_MEDIA_RECEIVER_APP_ID for a custom receiver if needed.

package com.nativestream.android.data.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(PLAYER_ACTIVITY_CLASS)
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_MEDIA_RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()

    companion object {
        private const val DEFAULT_MEDIA_RECEIVER_APP_ID = "CC1AD845"
        // MainActivity class name for notification tap-back
        private const val PLAYER_ACTIVITY_CLASS = "com.nativestream.android.MainActivity"
    }
}