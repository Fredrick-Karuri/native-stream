package com.nativestream.android.di;

import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.data.remote.ServerDiscoveryService;
import com.nativestream.android.data.remote.ServerHealthMonitor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideServerHealthMonitorFactory implements Factory<ServerHealthMonitor> {
  private final Provider<ApiClient> apiClientProvider;

  private final Provider<ServerDiscoveryService> discoveryServiceProvider;

  public AppModule_ProvideServerHealthMonitorFactory(Provider<ApiClient> apiClientProvider,
      Provider<ServerDiscoveryService> discoveryServiceProvider) {
    this.apiClientProvider = apiClientProvider;
    this.discoveryServiceProvider = discoveryServiceProvider;
  }

  @Override
  public ServerHealthMonitor get() {
    return provideServerHealthMonitor(apiClientProvider.get(), discoveryServiceProvider.get());
  }

  public static AppModule_ProvideServerHealthMonitorFactory create(
      javax.inject.Provider<ApiClient> apiClientProvider,
      javax.inject.Provider<ServerDiscoveryService> discoveryServiceProvider) {
    return new AppModule_ProvideServerHealthMonitorFactory(Providers.asDaggerProvider(apiClientProvider), Providers.asDaggerProvider(discoveryServiceProvider));
  }

  public static AppModule_ProvideServerHealthMonitorFactory create(
      Provider<ApiClient> apiClientProvider,
      Provider<ServerDiscoveryService> discoveryServiceProvider) {
    return new AppModule_ProvideServerHealthMonitorFactory(apiClientProvider, discoveryServiceProvider);
  }

  public static ServerHealthMonitor provideServerHealthMonitor(ApiClient apiClient,
      ServerDiscoveryService discoveryService) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideServerHealthMonitor(apiClient, discoveryService));
  }
}
