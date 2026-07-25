package com.nativestream.android.di;

import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.remote.ControlSession;
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
public final class AppModule_ProvideControlSessionFactory implements Factory<ControlSession> {
  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  public AppModule_ProvideControlSessionFactory(
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public ControlSession get() {
    return provideControlSession(settingsDataStoreProvider.get());
  }

  public static AppModule_ProvideControlSessionFactory create(
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new AppModule_ProvideControlSessionFactory(Providers.asDaggerProvider(settingsDataStoreProvider));
  }

  public static AppModule_ProvideControlSessionFactory create(
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new AppModule_ProvideControlSessionFactory(settingsDataStoreProvider);
  }

  public static ControlSession provideControlSession(SettingsDataStore settingsDataStore) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideControlSession(settingsDataStore));
  }
}
