package com.nativestream.android.data.cast;

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
public final class CastManager_Factory implements Factory<CastManager> {
  private final Provider<Context> contextProvider;

  public CastManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CastManager get() {
    return newInstance(contextProvider.get());
  }

  public static CastManager_Factory create(javax.inject.Provider<Context> contextProvider) {
    return new CastManager_Factory(Providers.asDaggerProvider(contextProvider));
  }

  public static CastManager_Factory create(Provider<Context> contextProvider) {
    return new CastManager_Factory(contextProvider);
  }

  public static CastManager newInstance(Context context) {
    return new CastManager(context);
  }
}
