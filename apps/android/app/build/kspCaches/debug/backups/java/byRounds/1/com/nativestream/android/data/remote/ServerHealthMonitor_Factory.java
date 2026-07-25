package com.nativestream.android.data.remote;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class ServerHealthMonitor_Factory implements Factory<ServerHealthMonitor> {
  private final Provider<ApiClient> apiClientProvider;

  private final Provider<ServerDiscoveryService> discoveryServiceProvider;

  public ServerHealthMonitor_Factory(Provider<ApiClient> apiClientProvider,
      Provider<ServerDiscoveryService> discoveryServiceProvider) {
    this.apiClientProvider = apiClientProvider;
    this.discoveryServiceProvider = discoveryServiceProvider;
  }

  @Override
  public ServerHealthMonitor get() {
    return newInstance(apiClientProvider.get(), discoveryServiceProvider.get());
  }

  public static ServerHealthMonitor_Factory create(
      javax.inject.Provider<ApiClient> apiClientProvider,
      javax.inject.Provider<ServerDiscoveryService> discoveryServiceProvider) {
    return new ServerHealthMonitor_Factory(Providers.asDaggerProvider(apiClientProvider), Providers.asDaggerProvider(discoveryServiceProvider));
  }

  public static ServerHealthMonitor_Factory create(Provider<ApiClient> apiClientProvider,
      Provider<ServerDiscoveryService> discoveryServiceProvider) {
    return new ServerHealthMonitor_Factory(apiClientProvider, discoveryServiceProvider);
  }

  public static ServerHealthMonitor newInstance(ApiClient apiClient,
      ServerDiscoveryService discoveryService) {
    return new ServerHealthMonitor(apiClient, discoveryService);
  }
}
