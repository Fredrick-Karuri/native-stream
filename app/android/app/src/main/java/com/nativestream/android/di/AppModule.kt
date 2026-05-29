// app/src/main/java/com/nativestream/android/di/AppModule.kt
//
// Hilt dependency injection module.
// Provides app-scoped singletons shared across the feature graph.
// ApiClient is @Singleton so a single Ktor HttpClient is reused everywhere.

package com.nativestream.android.di

import com.nativestream.android.data.remote.ApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient = ApiClient()
}