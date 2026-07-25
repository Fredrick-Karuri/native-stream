package com.nativestream.android.ui.viewmodel;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class FavouritesViewModel_Factory implements Factory<FavouritesViewModel> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public FavouritesViewModel_Factory(Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public FavouritesViewModel get() {
    return newInstance(dataStoreProvider.get());
  }

  public static FavouritesViewModel_Factory create(
      javax.inject.Provider<DataStore<Preferences>> dataStoreProvider) {
    return new FavouritesViewModel_Factory(Providers.asDaggerProvider(dataStoreProvider));
  }

  public static FavouritesViewModel_Factory create(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new FavouritesViewModel_Factory(dataStoreProvider);
  }

  public static FavouritesViewModel newInstance(DataStore<Preferences> dataStore) {
    return new FavouritesViewModel(dataStore);
  }
}
