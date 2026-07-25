package com.nativestream.android.data.remote;

import com.nativestream.android.data.local.SettingsDataStore;
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
public final class ControlSession_Factory implements Factory<ControlSession> {
  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  public ControlSession_Factory(Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public ControlSession get() {
    return newInstance(settingsDataStoreProvider.get());
  }

  public static ControlSession_Factory create(
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ControlSession_Factory(Providers.asDaggerProvider(settingsDataStoreProvider));
  }

  public static ControlSession_Factory create(
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ControlSession_Factory(settingsDataStoreProvider);
  }

  public static ControlSession newInstance(SettingsDataStore settingsDataStore) {
    return new ControlSession(settingsDataStore);
  }
}
