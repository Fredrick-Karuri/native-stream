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
public final class M3uParser_Factory implements Factory<M3uParser> {
  @Override
  public M3uParser get() {
    return newInstance();
  }

  public static M3uParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static M3uParser newInstance() {
    return new M3uParser();
  }

  private static final class InstanceHolder {
    static final M3uParser_Factory INSTANCE = new M3uParser_Factory();
  }
}
