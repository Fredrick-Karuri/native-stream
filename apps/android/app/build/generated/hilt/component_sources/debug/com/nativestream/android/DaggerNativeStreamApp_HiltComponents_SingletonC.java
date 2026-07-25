package com.nativestream.android;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.nativestream.android.data.cast.CastManager;
import com.nativestream.android.data.local.ChannelCache;
import com.nativestream.android.data.local.EpgIndexCache;
import com.nativestream.android.data.local.SettingsDataStore;
import com.nativestream.android.data.parser.EpgParser;
import com.nativestream.android.data.parser.M3uParser;
import com.nativestream.android.data.player.NativeStreamPlaybackService;
import com.nativestream.android.data.remote.ApiClient;
import com.nativestream.android.data.remote.ControlDiscoveryService;
import com.nativestream.android.data.remote.ControlSession;
import com.nativestream.android.data.remote.NetworkMonitor;
import com.nativestream.android.data.remote.ServerDiscoveryService;
import com.nativestream.android.data.remote.ServerHealthMonitor;
import com.nativestream.android.data.repository.ChannelRepositoryImpl;
import com.nativestream.android.di.AppModule_ProvideApiClientFactory;
import com.nativestream.android.di.AppModule_ProvideCastManagerFactory;
import com.nativestream.android.di.AppModule_ProvideControlDiscoveryServiceFactory;
import com.nativestream.android.di.AppModule_ProvideControlSessionFactory;
import com.nativestream.android.di.AppModule_ProvideServerDiscoveryServiceFactory;
import com.nativestream.android.di.AppModule_ProvideServerHealthMonitorFactory;
import com.nativestream.android.di.DataStoreModule_ProvideFavouritesDataStoreFactory;
import com.nativestream.android.di.DispatcherModule_ProvideIoDispatcherFactory;
import com.nativestream.android.ui.viewmodel.BrowseViewModel;
import com.nativestream.android.ui.viewmodel.BrowseViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.BrowseViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.BrowseViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.CastViewModel;
import com.nativestream.android.ui.viewmodel.CastViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.CastViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.CastViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ChannelFilterViewModel;
import com.nativestream.android.ui.viewmodel.ChannelFilterViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.ChannelFilterViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ChannelFilterViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel;
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ChannelManagerViewModel;
import com.nativestream.android.ui.viewmodel.ChannelManagerViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.ChannelManagerViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ChannelManagerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ControlViewModel;
import com.nativestream.android.ui.viewmodel.ControlViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.ControlViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ControlViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.EpgViewModel;
import com.nativestream.android.ui.viewmodel.EpgViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.EpgViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.EpgViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.FavouritesViewModel;
import com.nativestream.android.ui.viewmodel.FavouritesViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.FavouritesViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.FavouritesViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.NetworkViewModel;
import com.nativestream.android.ui.viewmodel.NetworkViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.NetworkViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.NetworkViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.PlayerViewModel;
import com.nativestream.android.ui.viewmodel.PlayerViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.PlayerViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.PlayerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ServerHealthViewModel;
import com.nativestream.android.ui.viewmodel.ServerHealthViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.ServerHealthViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.ServerHealthViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.SettingsViewModel;
import com.nativestream.android.ui.viewmodel.SettingsViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.nativestream.android.ui.viewmodel.SourceViewModel;
import com.nativestream.android.ui.viewmodel.SourceViewModel_HiltModules;
import com.nativestream.android.ui.viewmodel.SourceViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.nativestream.android.ui.viewmodel.SourceViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideApplicationFactory;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerNativeStreamApp_HiltComponents_SingletonC {
  private DaggerNativeStreamApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public NativeStreamApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements NativeStreamApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements NativeStreamApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements NativeStreamApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements NativeStreamApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements NativeStreamApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements NativeStreamApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements NativeStreamApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public NativeStreamApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends NativeStreamApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends NativeStreamApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends NativeStreamApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends NativeStreamApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(ImmutableMap.<String, Boolean>builderWithExpectedSize(13).put(BrowseViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, BrowseViewModel_HiltModules.KeyModule.provide()).put(CastViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, CastViewModel_HiltModules.KeyModule.provide()).put(ChannelFilterViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChannelFilterViewModel_HiltModules.KeyModule.provide()).put(ChannelLoadingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChannelLoadingViewModel_HiltModules.KeyModule.provide()).put(ChannelManagerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChannelManagerViewModel_HiltModules.KeyModule.provide()).put(ControlViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ControlViewModel_HiltModules.KeyModule.provide()).put(EpgViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EpgViewModel_HiltModules.KeyModule.provide()).put(FavouritesViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, FavouritesViewModel_HiltModules.KeyModule.provide()).put(NetworkViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, NetworkViewModel_HiltModules.KeyModule.provide()).put(PlayerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, PlayerViewModel_HiltModules.KeyModule.provide()).put(ServerHealthViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ServerHealthViewModel_HiltModules.KeyModule.provide()).put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()).put(SourceViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SourceViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends NativeStreamApp_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<BrowseViewModel> browseViewModelProvider;

    private Provider<CastViewModel> castViewModelProvider;

    private Provider<ChannelFilterViewModel> channelFilterViewModelProvider;

    private Provider<ChannelLoadingViewModel> channelLoadingViewModelProvider;

    private Provider<ChannelManagerViewModel> channelManagerViewModelProvider;

    private Provider<ControlViewModel> controlViewModelProvider;

    private Provider<EpgViewModel> epgViewModelProvider;

    private Provider<FavouritesViewModel> favouritesViewModelProvider;

    private Provider<NetworkViewModel> networkViewModelProvider;

    private Provider<PlayerViewModel> playerViewModelProvider;

    private Provider<ServerHealthViewModel> serverHealthViewModelProvider;

    private Provider<SettingsViewModel> settingsViewModelProvider;

    private Provider<SourceViewModel> sourceViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.browseViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.castViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.channelFilterViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.channelLoadingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.channelManagerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.controlViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.epgViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.favouritesViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.networkViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
      this.playerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 9);
      this.serverHealthViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 10);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 11);
      this.sourceViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 12);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(ImmutableMap.<String, javax.inject.Provider<ViewModel>>builderWithExpectedSize(13).put(BrowseViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) browseViewModelProvider)).put(CastViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) castViewModelProvider)).put(ChannelFilterViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) channelFilterViewModelProvider)).put(ChannelLoadingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) channelLoadingViewModelProvider)).put(ChannelManagerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) channelManagerViewModelProvider)).put(ControlViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) controlViewModelProvider)).put(EpgViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) epgViewModelProvider)).put(FavouritesViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) favouritesViewModelProvider)).put(NetworkViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) networkViewModelProvider)).put(PlayerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) playerViewModelProvider)).put(ServerHealthViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) serverHealthViewModelProvider)).put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) settingsViewModelProvider)).put(SourceViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) sourceViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return ImmutableMap.<Class<?>, Object>of();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.nativestream.android.ui.viewmodel.BrowseViewModel 
          return (T) new BrowseViewModel();

          case 1: // com.nativestream.android.ui.viewmodel.CastViewModel 
          return (T) new CastViewModel(singletonCImpl.provideCastManagerProvider.get());

          case 2: // com.nativestream.android.ui.viewmodel.ChannelFilterViewModel 
          return (T) new ChannelFilterViewModel(singletonCImpl.channelRepositoryImplProvider.get(), singletonCImpl.settingsDataStoreProvider.get());

          case 3: // com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel 
          return (T) new ChannelLoadingViewModel(singletonCImpl.provideApiClientProvider.get(), singletonCImpl.m3uParserProvider.get(), singletonCImpl.settingsDataStoreProvider.get(), singletonCImpl.channelCacheProvider.get(), singletonCImpl.channelRepositoryImplProvider.get(), DispatcherModule_ProvideIoDispatcherFactory.provideIoDispatcher());

          case 4: // com.nativestream.android.ui.viewmodel.ChannelManagerViewModel 
          return (T) new ChannelManagerViewModel(singletonCImpl.provideApiClientProvider.get());

          case 5: // com.nativestream.android.ui.viewmodel.ControlViewModel 
          return (T) new ControlViewModel(singletonCImpl.provideControlSessionProvider.get(), singletonCImpl.provideControlDiscoveryServiceProvider.get(), singletonCImpl.settingsDataStoreProvider.get());

          case 6: // com.nativestream.android.ui.viewmodel.EpgViewModel 
          return (T) new EpgViewModel(singletonCImpl.provideApiClientProvider.get(), singletonCImpl.epgParserProvider.get(), singletonCImpl.settingsDataStoreProvider.get(), singletonCImpl.epgIndexCacheProvider.get(), singletonCImpl.channelRepositoryImplProvider.get(), DispatcherModule_ProvideIoDispatcherFactory.provideIoDispatcher());

          case 7: // com.nativestream.android.ui.viewmodel.FavouritesViewModel 
          return (T) new FavouritesViewModel(singletonCImpl.provideFavouritesDataStoreProvider.get());

          case 8: // com.nativestream.android.ui.viewmodel.NetworkViewModel 
          return (T) new NetworkViewModel(singletonCImpl.networkMonitorProvider.get());

          case 9: // com.nativestream.android.ui.viewmodel.PlayerViewModel 
          return (T) new PlayerViewModel(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule), singletonCImpl.provideApiClientProvider.get(), singletonCImpl.channelRepositoryImplProvider.get());

          case 10: // com.nativestream.android.ui.viewmodel.ServerHealthViewModel 
          return (T) new ServerHealthViewModel(singletonCImpl.provideServerHealthMonitorProvider.get(), singletonCImpl.settingsDataStoreProvider.get(), singletonCImpl.provideApiClientProvider.get());

          case 11: // com.nativestream.android.ui.viewmodel.SettingsViewModel 
          return (T) new SettingsViewModel(singletonCImpl.settingsDataStoreProvider.get(), singletonCImpl.provideApiClientProvider.get(), singletonCImpl.provideServerDiscoveryServiceProvider.get());

          case 12: // com.nativestream.android.ui.viewmodel.SourceViewModel 
          return (T) new SourceViewModel(singletonCImpl.settingsDataStoreProvider.get(), singletonCImpl.channelCacheProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends NativeStreamApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends NativeStreamApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectNativeStreamPlaybackService(NativeStreamPlaybackService arg0) {
    }
  }

  private static final class SingletonCImpl extends NativeStreamApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<CastManager> provideCastManagerProvider;

    private Provider<ChannelRepositoryImpl> channelRepositoryImplProvider;

    private Provider<SettingsDataStore> settingsDataStoreProvider;

    private Provider<ApiClient> provideApiClientProvider;

    private Provider<M3uParser> m3uParserProvider;

    private Provider<ChannelCache> channelCacheProvider;

    private Provider<ControlSession> provideControlSessionProvider;

    private Provider<ControlDiscoveryService> provideControlDiscoveryServiceProvider;

    private Provider<EpgParser> epgParserProvider;

    private Provider<EpgIndexCache> epgIndexCacheProvider;

    private Provider<DataStore<Preferences>> provideFavouritesDataStoreProvider;

    private Provider<NetworkMonitor> networkMonitorProvider;

    private Provider<ServerDiscoveryService> provideServerDiscoveryServiceProvider;

    private Provider<ServerHealthMonitor> provideServerHealthMonitorProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideCastManagerProvider = DoubleCheck.provider(new SwitchingProvider<CastManager>(singletonCImpl, 0));
      this.channelRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ChannelRepositoryImpl>(singletonCImpl, 1));
      this.settingsDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<SettingsDataStore>(singletonCImpl, 2));
      this.provideApiClientProvider = DoubleCheck.provider(new SwitchingProvider<ApiClient>(singletonCImpl, 3));
      this.m3uParserProvider = DoubleCheck.provider(new SwitchingProvider<M3uParser>(singletonCImpl, 4));
      this.channelCacheProvider = DoubleCheck.provider(new SwitchingProvider<ChannelCache>(singletonCImpl, 5));
      this.provideControlSessionProvider = DoubleCheck.provider(new SwitchingProvider<ControlSession>(singletonCImpl, 6));
      this.provideControlDiscoveryServiceProvider = DoubleCheck.provider(new SwitchingProvider<ControlDiscoveryService>(singletonCImpl, 7));
      this.epgParserProvider = DoubleCheck.provider(new SwitchingProvider<EpgParser>(singletonCImpl, 8));
      this.epgIndexCacheProvider = DoubleCheck.provider(new SwitchingProvider<EpgIndexCache>(singletonCImpl, 9));
      this.provideFavouritesDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 10));
      this.networkMonitorProvider = DoubleCheck.provider(new SwitchingProvider<NetworkMonitor>(singletonCImpl, 11));
      this.provideServerDiscoveryServiceProvider = DoubleCheck.provider(new SwitchingProvider<ServerDiscoveryService>(singletonCImpl, 13));
      this.provideServerHealthMonitorProvider = DoubleCheck.provider(new SwitchingProvider<ServerHealthMonitor>(singletonCImpl, 12));
    }

    @Override
    public void injectNativeStreamApp(NativeStreamApp arg0) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return ImmutableSet.<Boolean>of();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.nativestream.android.data.cast.CastManager 
          return (T) AppModule_ProvideCastManagerFactory.provideCastManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 1: // com.nativestream.android.data.repository.ChannelRepositoryImpl 
          return (T) new ChannelRepositoryImpl();

          case 2: // com.nativestream.android.data.local.SettingsDataStore 
          return (T) new SettingsDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.nativestream.android.data.remote.ApiClient 
          return (T) AppModule_ProvideApiClientFactory.provideApiClient(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.settingsDataStoreProvider.get());

          case 4: // com.nativestream.android.data.parser.M3uParser 
          return (T) new M3uParser();

          case 5: // com.nativestream.android.data.local.ChannelCache 
          return (T) new ChannelCache(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          case 6: // com.nativestream.android.data.remote.ControlSession 
          return (T) AppModule_ProvideControlSessionFactory.provideControlSession(singletonCImpl.settingsDataStoreProvider.get());

          case 7: // com.nativestream.android.data.remote.ControlDiscoveryService 
          return (T) AppModule_ProvideControlDiscoveryServiceFactory.provideControlDiscoveryService(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 8: // com.nativestream.android.data.parser.EpgParser 
          return (T) new EpgParser();

          case 9: // com.nativestream.android.data.local.EpgIndexCache 
          return (T) new EpgIndexCache(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          case 10: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> 
          return (T) DataStoreModule_ProvideFavouritesDataStoreFactory.provideFavouritesDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 11: // com.nativestream.android.data.remote.NetworkMonitor 
          return (T) new NetworkMonitor(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 12: // com.nativestream.android.data.remote.ServerHealthMonitor 
          return (T) AppModule_ProvideServerHealthMonitorFactory.provideServerHealthMonitor(singletonCImpl.provideApiClientProvider.get(), singletonCImpl.provideServerDiscoveryServiceProvider.get());

          case 13: // com.nativestream.android.data.remote.ServerDiscoveryService 
          return (T) AppModule_ProvideServerDiscoveryServiceFactory.provideServerDiscoveryService(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideApiClientProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
