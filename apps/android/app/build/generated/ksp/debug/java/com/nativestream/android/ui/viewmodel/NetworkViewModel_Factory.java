package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.remote.NetworkMonitor;
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
public final class NetworkViewModel_Factory implements Factory<NetworkViewModel> {
  private final Provider<NetworkMonitor> networkMonitorProvider;

  public NetworkViewModel_Factory(Provider<NetworkMonitor> networkMonitorProvider) {
    this.networkMonitorProvider = networkMonitorProvider;
  }

  @Override
  public NetworkViewModel get() {
    return newInstance(networkMonitorProvider.get());
  }

  public static NetworkViewModel_Factory create(
      javax.inject.Provider<NetworkMonitor> networkMonitorProvider) {
    return new NetworkViewModel_Factory(Providers.asDaggerProvider(networkMonitorProvider));
  }

  public static NetworkViewModel_Factory create(Provider<NetworkMonitor> networkMonitorProvider) {
    return new NetworkViewModel_Factory(networkMonitorProvider);
  }

  public static NetworkViewModel newInstance(NetworkMonitor networkMonitor) {
    return new NetworkViewModel(networkMonitor);
  }
}
