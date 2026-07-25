package com.nativestream.android.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ChannelRepositoryImpl_Factory implements Factory<ChannelRepositoryImpl> {
  @Override
  public ChannelRepositoryImpl get() {
    return newInstance();
  }

  public static ChannelRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ChannelRepositoryImpl newInstance() {
    return new ChannelRepositoryImpl();
  }

  private static final class InstanceHolder {
    static final ChannelRepositoryImpl_Factory INSTANCE = new ChannelRepositoryImpl_Factory();
  }
}
