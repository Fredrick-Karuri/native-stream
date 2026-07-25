package com.nativestream.android.data.local;

import android.app.Application;
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
public final class ChannelCache_Factory implements Factory<ChannelCache> {
  private final Provider<Application> applicationProvider;

  public ChannelCache_Factory(Provider<Application> applicationProvider) {
    this.applicationProvider = applicationProvider;
  }

  @Override
  public ChannelCache get() {
    return newInstance(applicationProvider.get());
  }

  public static ChannelCache_Factory create(
      javax.inject.Provider<Application> applicationProvider) {
    return new ChannelCache_Factory(Providers.asDaggerProvider(applicationProvider));
  }

  public static ChannelCache_Factory create(Provider<Application> applicationProvider) {
    return new ChannelCache_Factory(applicationProvider);
  }

  public static ChannelCache newInstance(Application application) {
    return new ChannelCache(application);
  }
}
