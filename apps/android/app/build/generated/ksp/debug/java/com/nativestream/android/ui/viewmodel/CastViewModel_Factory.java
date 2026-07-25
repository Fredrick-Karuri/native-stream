package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.cast.CastManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class CastViewModel_Factory implements Factory<CastViewModel> {
  private final Provider<CastManager> castManagerProvider;

  public CastViewModel_Factory(Provider<CastManager> castManagerProvider) {
    this.castManagerProvider = castManagerProvider;
  }

  @Override
  public CastViewModel get() {
    return newInstance(castManagerProvider.get());
  }

  public static CastViewModel_Factory create(
      javax.inject.Provider<CastManager> castManagerProvider) {
    return new CastViewModel_Factory(Providers.asDaggerProvider(castManagerProvider));
  }

  public static CastViewModel_Factory create(Provider<CastManager> castManagerProvider) {
    return new CastViewModel_Factory(castManagerProvider);
  }

  public static CastViewModel newInstance(CastManager castManager) {
    return new CastViewModel(castManager);
  }
}
