package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.ChannelCache;
import com.nativestream.android.data.local.SettingsDataStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
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
public final class SourceViewModel_Factory implements Factory<SourceViewModel> {
  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private final Provider<ChannelCache> channelCacheProvider;

  public SourceViewModel_Factory(Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ChannelCache> channelCacheProvider) {
    this.settingsDataStoreProvider = settingsDataStoreProvider;
    this.channelCacheProvider = channelCacheProvider;
  }

  @Override
  public SourceViewModel get() {
    return newInstance(settingsDataStoreProvider.get(), channelCacheProvider.get());
  }

  public static SourceViewModel_Factory create(
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider,
      javax.inject.Provider<ChannelCache> channelCacheProvider) {
    return new SourceViewModel_Factory(Providers.asDaggerProvider(settingsDataStoreProvider), Providers.asDaggerProvider(channelCacheProvider));
  }

  public static SourceViewModel_Factory create(
      Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ChannelCache> channelCacheProvider) {
    return new SourceViewModel_Factory(settingsDataStoreProvider, channelCacheProvider);
  }

  public static SourceViewModel newInstance(SettingsDataStore settingsDataStore,
      ChannelCache channelCache) {
    return new SourceViewModel(settingsDataStore, channelCache);
  }
}
