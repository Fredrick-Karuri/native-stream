package com.nativestream.android.data.remote;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ServerDiscoveryService_Factory implements Factory<ServerDiscoveryService> {
  private final Provider<Context> contextProvider;

  private final Provider<ApiClient> apiClientProvider;

  public ServerDiscoveryService_Factory(Provider<Context> contextProvider,
      Provider<ApiClient> apiClientProvider) {
    this.contextProvider = contextProvider;
    this.apiClientProvider = apiClientProvider;
  }

  @Override
  public ServerDiscoveryService get() {
    return newInstance(contextProvider.get(), apiClientProvider.get());
  }

  public static ServerDiscoveryService_Factory create(
      javax.inject.Provider<Context> contextProvider,
      javax.inject.Provider<ApiClient> apiClientProvider) {
    return new ServerDiscoveryService_Factory(Providers.asDaggerProvider(contextProvider), Providers.asDaggerProvider(apiClientProvider));
  }

  public static ServerDiscoveryService_Factory create(Provider<Context> contextProvider,
      Provider<ApiClient> apiClientProvider) {
    return new ServerDiscoveryService_Factory(contextProvider, apiClientProvider);
  }

  public static ServerDiscoveryService newInstance(Context context, ApiClient apiClient) {
    return new ServerDiscoveryService(context, apiClient);
  }
}
