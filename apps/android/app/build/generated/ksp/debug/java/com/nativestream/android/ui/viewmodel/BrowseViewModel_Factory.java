package com.nativestream.android.ui.viewmodel;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class BrowseViewModel_Factory implements Factory<BrowseViewModel> {
  @Override
  public BrowseViewModel get() {
    return newInstance();
  }

  public static BrowseViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static BrowseViewModel newInstance() {
    return new BrowseViewModel();
  }

  private static final class InstanceHolder {
    static final BrowseViewModel_Factory INSTANCE = new BrowseViewModel_Factory();
  }
}
