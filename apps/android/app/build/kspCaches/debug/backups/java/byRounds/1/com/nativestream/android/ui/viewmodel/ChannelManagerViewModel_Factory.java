package com.nativestream.android.ui.viewmodel;

import com.nativestream.android.data.remote.ApiClient;
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
public final class ChannelManagerViewModel_Factory implements Factory<ChannelManagerViewModel> {
  private final Provider<ApiClient> apiClientProvider;

  public ChannelManagerViewModel_Factory(Provider<ApiClient> apiClientProvider) {
    this.apiClientProvider = apiClientProvider;
  }

  @Override
  public ChannelManagerViewModel get() {
    return newInstance(apiClientProvider.get());
  }

  public static ChannelManagerViewModel_Factory create(
      javax.inject.Provider<ApiClient> apiClientProvider) {
    return new ChannelManagerViewModel_Factory(Providers.asDaggerProvider(apiClientProvider));
  }

  public static ChannelManagerViewModel_Factory create(Provider<ApiClient> apiClientProvider) {
    return new ChannelManagerViewModel_Factory(apiClientProvider);
  }

  public static ChannelManagerViewModel newInstance(ApiClient apiClient) {
    return new ChannelManagerViewModel(apiClient);
  }
}
