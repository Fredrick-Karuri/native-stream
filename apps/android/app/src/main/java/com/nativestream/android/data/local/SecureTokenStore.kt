// app/src/main/java/com/nativestream/android/data/local/SecureTokenStore.kt
//
// Stores the hosted-server API token via EncryptedSharedPreferences,
// not DataStore<Preferences> like SettingsDataStore — the token is a secret,
// everything else in SettingsDataStore isn't. Mixing them into one store would
// make it easy to accidentally add a future secret field as plain text by
// just following the existing pattern in that file. Keeping storage APIs
// distinct keeps "this field is encrypted" visible at the type/file level
// rather than something you have to remember per-key.

package com.nativestream.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "ns_secure_settings"
private const val KEY_API_TOKEN = "api_token"

@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiToken(): String? = prefs.getString(KEY_API_TOKEN, null)?.ifEmpty { null }

    fun setApiToken(token: String) {
        prefs.edit().putString(KEY_API_TOKEN, token).apply()
    }

    fun clearApiToken() {
        prefs.edit().remove(KEY_API_TOKEN).apply()
    }
}