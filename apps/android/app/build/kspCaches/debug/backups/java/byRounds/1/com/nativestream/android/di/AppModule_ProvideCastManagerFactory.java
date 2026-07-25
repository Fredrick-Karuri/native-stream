package com.nativestream.android.di;

import android.content.Context;
import com.nativestream.android.data.cast.CastManager;
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
public final class AppModule_ProvideCastManagerFactory implements Factory<CastManager> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideCastManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CastManager get() {
    return provideCastManager(contextProvider.get());
  }

  public static AppModule_ProvideCastManagerFactory create(
      javax.inject.Provider<Context> contextProvider) {
    return new AppModule_ProvideCastManagerFactory(Providers.asDaggerProvider(contextProvider));
  }

  public static AppModule_ProvideCastManagerFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideCastManagerFactory(contextProvider);
  }

  public static CastManager provideCastManager(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideCastManager(context));
  }
}
