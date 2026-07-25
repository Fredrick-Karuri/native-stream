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
public final class ControlDiscoveryService_Factory implements Factory<ControlDiscoveryService> {
  private final Provider<Context> contextProvider;

  public ControlDiscoveryService_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ControlDiscoveryService get() {
    return newInstance(contextProvider.get());
  }

  public static ControlDiscoveryService_Factory create(
      javax.inject.Provider<Context> contextProvider) {
    return new ControlDiscoveryService_Factory(Providers.asDaggerProvider(contextProvider));
  }

  public static ControlDiscoveryService_Factory create(Provider<Context> contextProvider) {
    return new ControlDiscoveryService_Factory(contextProvider);
  }

  public static ControlDiscoveryService newInstance(Context context) {
    return new ControlDiscoveryService(context);
  }
}
