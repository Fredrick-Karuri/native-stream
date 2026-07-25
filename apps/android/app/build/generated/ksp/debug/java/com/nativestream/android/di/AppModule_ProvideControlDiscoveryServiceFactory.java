package com.nativestream.android.di;

import android.content.Context;
import com.nativestream.android.data.remote.ControlDiscoveryService;
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
public final class AppModule_ProvideControlDiscoveryServiceFactory implements Factory<ControlDiscoveryService> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideControlDiscoveryServiceFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ControlDiscoveryService get() {
    return provideControlDiscoveryService(contextProvider.get());
  }

  public static AppModule_ProvideControlDiscoveryServiceFactory create(
      javax.inject.Provider<Context> contextProvider) {
    return new AppModule_ProvideControlDiscoveryServiceFactory(Providers.asDaggerProvider(contextProvider));
  }

  public static AppModule_ProvideControlDiscoveryServiceFactory create(
      Provider<Context> contextProvider) {
    return new AppModule_ProvideControlDiscoveryServiceFactory(contextProvider);
  }

  public static ControlDiscoveryService provideControlDiscoveryService(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideControlDiscoveryService(context));
  }
}
