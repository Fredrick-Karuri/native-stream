package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.domain.repository.ChannelRepository;
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
public final class ChannelFilterViewModel_Factory implements Factory<ChannelFilterViewModel> {
  private final Provider<ChannelRepository> channelRepositoryProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  public ChannelFilterViewModel_Factory(Provider<ChannelRepository> channelRepositoryProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.channelRepositoryProvider = channelRepositoryProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public ChannelFilterViewModel get() {
    return newInstance(channelRepositoryProvider.get(), settingsDataStoreProvider.get());
  }

  public static ChannelFilterViewModel_Factory create(
      javax.inject.Provider<ChannelRepository> channelRepositoryProvider,
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ChannelFilterViewModel_Factory(Providers.asDaggerProvider(channelRepositoryProvider), Providers.asDaggerProvider(settingsDataStoreProvider));
  }

  public static ChannelFilterViewModel_Factory create(
      Provider<ChannelRepository> channelRepositoryProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ChannelFilterViewModel_Factory(channelRepositoryProvider, settingsDataStoreProvider);
  }

  public static ChannelFilterViewModel newInstance(ChannelRepository channelRepository,
      SettingsDataStore settingsDataStore) {
    return new ChannelFilterViewModel(channelRepository, settingsDataStore);
  }
}
