package com.nativestream.android.di;

import android.content.Context;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.data.remote.ServerDiscoveryService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideServerDiscoveryServiceFactory implements Factory<ServerDiscoveryService> {
  private final Provider<Context> contextProvider;

  private final Provider<ApiClient> apiClientProvider;

  public AppModule_ProvideServerDiscoveryServiceFactory(Provider<Context> contextProvider,
      Provider<ApiClient> apiClientProvider) {
    this.contextProvider = contextProvider;
    this.apiClientProvider = apiClientProvider;
  }

  @Override
  public ServerDiscoveryService get() {
    return provideServerDiscoveryService(contextProvider.get(), apiClientProvider.get());
  }

  public static AppModule_ProvideServerDiscoveryServiceFactory create(
      javax.inject.Provider<Context> contextProvider,
      javax.inject.Provider<ApiClient> apiClientProvider) {
    return new AppModule_ProvideServerDiscoveryServiceFactory(Providers.asDaggerProvider(contextProvider), Providers.asDaggerProvider(apiClientProvider));
  }

  public static AppModule_ProvideServerDiscoveryServiceFactory create(
      Provider<Context> contextProvider, Provider<ApiClient> apiClientProvider) {
    return new AppModule_ProvideServerDiscoveryServiceFactory(contextProvider, apiClientProvider);
  }

  public static ServerDiscoveryService provideServerDiscoveryService(Context context,
      ApiClient apiClient) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideServerDiscoveryService(context, apiClient));
  }
}
