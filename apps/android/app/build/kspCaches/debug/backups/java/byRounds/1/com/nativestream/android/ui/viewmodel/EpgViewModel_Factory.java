package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.EpgIndexCache;
import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.parser.EpgParser;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.domain.repository.ChannelRepository;
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
public final class EpgViewModel_Factory implements Factory<EpgViewModel> {
  private final Provider<ApiClient> apiClientProvider;

  private final Provider<EpgParser> epgParserProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private final Provider<EpgIndexCache> epgIndexCacheProvider;

  private final Provider<ChannelRepository> channelRepositoryProvider;

  private final Provider<CoroutineDispatcher> ioDispatcherProvider;

  public EpgViewModel_Factory(Provider<ApiClient> apiClientProvider,
      Provider<EpgParser> epgParserProvider, Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<EpgIndexCache> epgIndexCacheProvider,
      Provider<ChannelRepository> channelRepositoryProvider,
      Provider<CoroutineDispatcher> ioDispatcherProvider) {
    this.apiClientProvider = apiClientProvider;
    this.epgParserProvider = epgParserProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
    this.epgIndexCacheProvider = epgIndexCacheProvider;
    this.channelRepositoryProvider = channelRepositoryProvider;
    this.ioDispatcherProvider = ioDispatcherProvider;
  }

  @Override
  public EpgViewModel get() {
    return newInstance(apiClientProvider.get(), epgParserProvider.get(), settingsDataStoreProvider.get(), epgIndexCacheProvider.get(), channelRepositoryProvider.get(), ioDispatcherProvider.get());
  }

  public static EpgViewModel_Factory create(javax.inject.Provider<ApiClient> apiClientProvider,
      javax.inject.Provider<EpgParser> epgParserProvider,
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider,
      javax.inject.Provider<EpgIndexCache> epgIndexCacheProvider,
      javax.inject.Provider<ChannelRepository> channelRepositoryProvider,
      javax.inject.Provider<CoroutineDispatcher> ioDispatcherProvider) {
    return new EpgViewModel_Factory(Providers.asDaggerProvider(apiClientProvider), Providers.asDaggerProvider(epgParserProvider), Providers.asDaggerProvider(settingsDataStoreProvider), Providers.asDaggerProvider(epgIndexCacheProvider), Providers.asDaggerProvider(channelRepositoryProvider), Providers.asDaggerProvider(ioDispatcherProvider));
  }

  public static EpgViewModel_Factory create(Provider<ApiClient> apiClientProvider,
      Provider<EpgParser> epgParserProvider, Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<EpgIndexCache> epgIndexCacheProvider,
      Provider<ChannelRepository> channelRepositoryProvider,
      Provider<CoroutineDispatcher> ioDispatcherProvider) {
    return new EpgViewModel_Factory(apiClientProvider, epgParserProvider, settingsDataStoreProvider, epgIndexCacheProvider, channelRepositoryProvider, ioDispatcherProvider);
  }

  public static EpgViewModel newInstance(ApiClient apiClient, EpgParser epgParser,
      SettingsDataStore settingsDataStore, EpgIndexCache epgIndexCache,
      ChannelRepository channelRepository, CoroutineDispatcher ioDispatcher) {
    return new EpgViewModel(apiClient, epgParser, settingsDataStore, epgIndexCache, channelRepository, ioDispatcher);
  }
}
