package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.ChannelCache;
import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.parser.M3uParser;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.data.repository.ChannelRepositoryImpl;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("javax.inject.Named")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class ChannelLoadingViewModel_Factory implements Factory<ChannelLoadingViewModel> {
  private final Provider<ApiClient> apiClientProvider;

  private final Provider<M3uParser> m3uParserProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private final Provider<ChannelCache> channelCacheProvider;

  private final Provider<ChannelRepositoryImpl> channelRepositoryProvider;

  private final Provider<CoroutineDispatcher> ioDispatcherProvider;

  public ChannelLoadingViewModel_Factory(Provider<ApiClient> apiClientProvider,
      Provider<M3uParser> m3uParserProvider, Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ChannelCache> channelCacheProvider,
      Provider<ChannelRepositoryImpl> channelRepositoryProvider,
      Provider<CoroutineDispatcher> ioDispatcherProvider) {
    this.apiClientProvider = apiClientProvider;
    this.m3uParserProvider = m3uParserProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
    this.channelCacheProvider = channelCacheProvider;
    this.channelRepositoryProvider = channelRepositoryProvider;
    this.ioDispatcherProvider = ioDispatcherProvider;
  }

  @Override
  public ChannelLoadingViewModel get() {
    return newInstance(apiClientProvider.get(), m3uParserProvider.get(), settingsDataStoreProvider.get(), channelCacheProvider.get(), channelRepositoryProvider.get(), ioDispatcherProvider.get());
  }

  public static ChannelLoadingViewModel_Factory create(
      javax.inject.Provider<ApiClient> apiClientProvider,
      javax.inject.Provider<M3uParser> m3uParserProvider,
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider,
      javax.inject.Provider<ChannelCache> channelCacheProvider,
      javax.inject.Provider<ChannelRepositoryImpl> channelRepositoryProvider,
      javax.inject.Provider<CoroutineDispatcher> ioDispatcherProvider) {
    return new ChannelLoadingViewModel_Factory(Providers.asDaggerProvider(apiClientProvider), Providers.asDaggerProvider(m3uParserProvider), Providers.asDaggerProvider(settingsDataStoreProvider), Providers.asDaggerProvider(channelCacheProvider), Providers.asDaggerProvider(channelRepositoryProvider), Providers.asDaggerProvider(ioDispatcherProvider));
  }

  public static ChannelLoadingViewModel_Factory create(Provider<ApiClient> apiClientProvider,
      Provider<M3uParser> m3uParserProvider, Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ChannelCache> channelCacheProvider,
      Provider<ChannelRepositoryImpl> channelRepositoryProvider,
      Provider<CoroutineDispatcher> ioDispatcherProvider) {
    return new ChannelLoadingViewModel_Factory(apiClientProvider, m3uParserProvider, settingsDataStoreProvider, channelCacheProvider, channelRepositoryProvider, ioDispatcherProvider);
  }

  public static ChannelLoadingViewModel newInstance(ApiClient apiClient, M3uParser m3uParser,
      SettingsDataStore settingsDataStore, ChannelCache channelCache,
      ChannelRepositoryImpl channelRepository, CoroutineDispatcher ioDispatcher) {
    return new ChannelLoadingViewModel(apiClient, m3uParser, settingsDataStore, channelCache, channelRepository, ioDispatcher);
  }
}
