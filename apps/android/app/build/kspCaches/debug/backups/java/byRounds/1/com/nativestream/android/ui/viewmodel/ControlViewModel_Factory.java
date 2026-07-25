package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.remote.ControlDiscoveryService;
import com.nativestream.android.data.remote.ControlSession;
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
public final class ControlViewModel_Factory implements Factory<ControlViewModel> {
  private final Provider<ControlSession> controlSessionProvider;

  private final Provider<ControlDiscoveryService> controlDiscoveryProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  public ControlViewModel_Factory(Provider<ControlSession> controlSessionProvider,
      Provider<ControlDiscoveryService> controlDiscoveryProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.controlSessionProvider = controlSessionProvider;
    this.controlDiscoveryProvider = controlDiscoveryProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public ControlViewModel get() {
    return newInstance(controlSessionProvider.get(), controlDiscoveryProvider.get(), settingsDataStoreProvider.get());
  }

  public static ControlViewModel_Factory create(
      javax.inject.Provider<ControlSession> controlSessionProvider,
      javax.inject.Provider<ControlDiscoveryService> controlDiscoveryProvider,
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ControlViewModel_Factory(Providers.asDaggerProvider(controlSessionProvider), Providers.asDaggerProvider(controlDiscoveryProvider), Providers.asDaggerProvider(settingsDataStoreProvider));
  }

  public static ControlViewModel_Factory create(Provider<ControlSession> controlSessionProvider,
      Provider<ControlDiscoveryService> controlDiscoveryProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ControlViewModel_Factory(controlSessionProvider, controlDiscoveryProvider, settingsDataStoreProvider);
  }

  public static ControlViewModel newInstance(ControlSession controlSession,
      ControlDiscoveryService controlDiscovery, SettingsDataStore settingsDataStore) {
    return new ControlViewModel(controlSession, controlDiscovery, settingsDataStore);
  }
}
