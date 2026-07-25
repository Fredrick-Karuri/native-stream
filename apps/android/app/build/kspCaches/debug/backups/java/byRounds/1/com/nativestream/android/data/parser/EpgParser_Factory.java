package com.nativestream.android.data.parser;

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
public final class EpgParser_Factory implements Factory<EpgParser> {
  @Override
  public EpgParser get() {
    return newInstance();
  }

  public static EpgParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static EpgParser newInstance() {
    return new EpgParser();
  }

  private static final class InstanceHolder {
    static final EpgParser_Factory INSTANCE = new EpgParser_Factory();
  }
}
