package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.data.remote.ServerDiscoveryService;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private final Provider<ApiClient> apiClientProvider;

  private final Provider<ServerDiscoveryService> discoveryServiceProvider;

  public SettingsViewModel_Factory(Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ApiClient> apiClientProvider,
      Provider<ServerDiscoveryService> discoveryServiceProvider) {
    this.settingsDataStoreProvider = settingsDataStoreProvider;
    this.apiClientProvider = apiClientProvider;
    this.discoveryServiceProvider = discoveryServiceProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(settingsDataStoreProvider.get(), apiClientProvider.get(), discoveryServiceProvider.get());
  }

  public static SettingsViewModel_Factory create(
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider,
      javax.inject.Provider<ApiClient> apiClientProvider,
      javax.inject.Provider<ServerDiscoveryService> discoveryServiceProvider) {
    return new SettingsViewModel_Factory(Providers.asDaggerProvider(settingsDataStoreProvider), Providers.asDaggerProvider(apiClientProvider), Providers.asDaggerProvider(discoveryServiceProvider));
  }

  public static SettingsViewModel_Factory create(
      Provider<SettingsDataStore> settingsDataStoreProvider, Provider<ApiClient> apiClientProvider,
      Provider<ServerDiscoveryService> discoveryServiceProvider) {
    return new SettingsViewModel_Factory(settingsDataStoreProvider, apiClientProvider, discoveryServiceProvider);
  }

  public static SettingsViewModel newInstance(SettingsDataStore settingsDataStore,
      ApiClient apiClient, ServerDiscoveryService discoveryService) {
    return new SettingsViewModel(settingsDataStore, apiClient, discoveryService);
  }
}
