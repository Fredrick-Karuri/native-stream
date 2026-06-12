// app/src/main/java/com/nativestream/android/di/AppModule.kt
//
// Hilt DI module — app-scoped singletons.

package com.nativestream.android.di

import android.app.Application
import android.content.Context
import com.nativestream.android.data.cast.CastManager
import com.nativestream.android.data.local.SettingsDataStore
import com.nativestream.android.data.remote.ApiClient
import com.nativestream.android.data.remote.ServerDiscoveryService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideApiClient(
        @ApplicationContext context: Context,
        settingsDataStore: SettingsDataStore
    ): ApiClient {
        val client = ApiClient(context as Application)
        val url = runBlocking { settingsDataStore.serverUrl.first() }
        client.setBaseUrl(url)
        return client
    }
    @Provides @Singleton
    fun provideCastManager(@ApplicationContext context: Context): CastManager =
        CastManager(context)
    @Provides @Singleton
    fun provideServerDiscoveryService(
        @ApplicationContext context: Context,
        apiClient: ApiClient,
    ): ServerDiscoveryService = ServerDiscoveryService(context, apiClient)
}