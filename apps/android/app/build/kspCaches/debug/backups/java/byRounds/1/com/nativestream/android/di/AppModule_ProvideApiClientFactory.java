package com.nativestream.android.di;

import android.content.Context;
import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.remote.ApiClient;
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
public final class AppModule_ProvideApiClientFactory implements Factory<ApiClient> {
  private final Provider<Context> contextProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  public AppModule_ProvideApiClientFactory(Provider<Context> contextProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.contextProvider = contextProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public ApiClient get() {
    return provideApiClient(contextProvider.get(), settingsDataStoreProvider.get());
  }

  public static AppModule_ProvideApiClientFactory create(
      javax.inject.Provider<Context> contextProvider,
      javax.inject.Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new AppModule_ProvideApiClientFactory(Providers.asDaggerProvider(contextProvider), Providers.asDaggerProvider(settingsDataStoreProvider));
  }

  public static AppModule_ProvideApiClientFactory create(Provider<Context> contextProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new AppModule_ProvideApiClientFactory(contextProvider, settingsDataStoreProvider);
  }

  public static ApiClient provideApiClient(Context context, SettingsDataStore settingsDataStore) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideApiClient(context, settingsDataStore));
  }
}
