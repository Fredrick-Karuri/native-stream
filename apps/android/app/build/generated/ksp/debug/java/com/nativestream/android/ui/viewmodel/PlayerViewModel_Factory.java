package com.nativestream.android.ui.viewmodel;

import android.app.Application;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.domain.repository.ChannelRepository;
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
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<Application> applicationProvider;

  private final Provider<ApiClient> apiClientProvider;

  private final Provider<ChannelRepository> channelRepositoryProvider;

  public PlayerViewModel_Factory(Provider<Application> applicationProvider,
      Provider<ApiClient> apiClientProvider,
      Provider<ChannelRepository> channelRepositoryProvider) {
    this.applicationProvider = applicationProvider;
    this.apiClientProvider = apiClientProvider;
    this.channelRepositoryProvider = channelRepositoryProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(applicationProvider.get(), apiClientProvider.get(), channelRepositoryProvider.get());
  }

  public static PlayerViewModel_Factory create(
      javax.inject.Provider<Application> applicationProvider,
      javax.inject.Provider<ApiClient> apiClientProvider,
      javax.inject.Provider<ChannelRepository> channelRepositoryProvider) {
    return new PlayerViewModel_Factory(Providers.asDaggerProvider(applicationProvider), Providers.asDaggerProvider(apiClientProvider), Providers.asDaggerProvider(channelRepositoryProvider));
  }

  public static PlayerViewModel_Factory create(Provider<Application> applicationProvider,
      Provider<ApiClient> apiClientProvider,
      Provider<ChannelRepository> channelRepositoryProvider) {
    return new PlayerViewModel_Factory(applicationProvider, apiClientProvider, channelRepositoryProvider);
  }

  public static PlayerViewModel newInstance(Application application, ApiClient apiClient,
      ChannelRepository channelRepository) {
    return new PlayerViewModel(application, apiClient, channelRepository);
  }
}
