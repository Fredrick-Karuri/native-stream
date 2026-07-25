package com.nativestream.android.data.remote;

import android.app.Application;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.engine.HttpClientEngine;
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
public final class ApiClient_Factory implements Factory<ApiClient> {
  private final Provider<Application> applicationProvider;

  private final Provider<HttpClientEngine> engineProvider;

  public ApiClient_Factory(Provider<Application> applicationProvider,
      Provider<HttpClientEngine> engineProvider) {
    this.applicationProvider = applicationProvider;
    this.engineProvider = engineProvider;
  }

  @Override
  public ApiClient get() {
    return newInstance(applicationProvider.get(), engineProvider.get());
  }

  public static ApiClient_Factory create(javax.inject.Provider<Application> applicationProvider,
      javax.inject.Provider<HttpClientEngine> engineProvider) {
    return new ApiClient_Factory(Providers.asDaggerProvider(applicationProvider), Providers.asDaggerProvider(engineProvider));
  }

  public static ApiClient_Factory create(Provider<Application> applicationProvider,
      Provider<HttpClientEngine> engineProvider) {
    return new ApiClient_Factory(applicationProvider, engineProvider);
  }

  public static ApiClient newInstance(Application application, HttpClientEngine engine) {
    return new ApiClient(application, engine);
  }
}
