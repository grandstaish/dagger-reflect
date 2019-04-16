package com.example;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Component(modules = ProviderScopedBinding.Module1.class)
interface ProviderScopedBinding {
  Provider<String> value();

  @Module
  abstract class Module1 {
    static final AtomicInteger oneCount = new AtomicInteger(0);

    @Singleton @Provides static String one() {
      return "one" + oneCount.getAndIncrement();
    }
  }
}
