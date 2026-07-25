package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.data.remote.ServerHealthMonitor;
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
public final class ServerHealthViewModel_Factory implements Factory<ServerHealthViewModel> {
  private final Provider<ServerHealthMonitor> monitorProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private final Provider<ApiClient> apiClientProvider;

  public ServerHealthViewModel_Factory(Provider<ServerHealthMonitor> monitorProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ApiClient> apiClientProvider) {
    this.monitorProvider = monitorProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
    this.apiClientProvider = apiClientProvider;
  }

  @Override
  public ServerHealthViewModel get() {
    return newInstance(monitorProvider.get(), settingsDataStoreProvider.get(), apiClientProvider.get());
  }

  public static ServerHealthViewModel_Factory create(
      javax.inject.Provider<ServerHealthMonitor> monitorProvider,
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider,
      javax.inject.Provider<ApiClient> apiClientProvider) {
    return new ServerHealthViewModel_Factory(Providers.asDaggerProvider(monitorProvider), Providers.asDaggerProvider(settingsDataStoreProvider), Providers.asDaggerProvider(apiClientProvider));
  }

  public static ServerHealthViewModel_Factory create(Provider<ServerHealthMonitor> monitorProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<ApiClient> apiClientProvider) {
    return new ServerHealthViewModel_Factory(monitorProvider, settingsDataStoreProvider, apiClientProvider);
  }

  public static ServerHealthViewModel newInstance(ServerHealthMonitor monitor,
      SettingsDataStore settingsDataStore, ApiClient apiClient) {
    return new ServerHealthViewModel(monitor, settingsDataStore, apiClient);
  }
}
