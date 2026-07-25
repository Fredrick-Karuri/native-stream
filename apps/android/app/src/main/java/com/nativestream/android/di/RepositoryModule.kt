/**
 * app/src/main/java/com/nativestream/android/di/RepositoryModule.kt
 *
 * Hilt bindings for repository interfaces. Kept as a separate abstract class
 * from AppModule (which is an object) because @Binds requires an abstract module.
 * Installed in SingletonComponent to match ChannelRepositoryImpl's @Singleton scope.
 */

package com.nativestream.android.di

import com.nativestream.android.data.repository.ChannelRepositoryImpl
import com.nativestream.android.domain.repository.ChannelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChannelRepository(
        impl: ChannelRepositoryImpl,
    ): ChannelRepository
}