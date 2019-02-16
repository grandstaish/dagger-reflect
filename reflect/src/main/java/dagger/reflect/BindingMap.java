/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.reflect;

import dagger.reflect.Binding.LinkedBinding;
import dagger.reflect.Binding.UnlinkedBinding;
import dagger.reflect.TypeUtil.ParameterizedTypeImpl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

final class BindingMap {
  private final ConcurrentHashMap<Key, Binding> bindings;
  private final JustInTimeProvider jitProvider;

  private BindingMap(ConcurrentHashMap<Key, Binding> bindings, JustInTimeProvider jitProvider) {
    this.bindings = bindings;
    this.jitProvider = jitProvider;
  }

  @Nullable Binding get(Key key) {
    Binding binding = bindings.get(key);
    if (binding != null) {
      return binding;
    }

    Binding jitBinding = jitProvider.create(key);
    if (jitBinding != null) {
      Binding replaced = bindings.putIfAbsent(key, jitBinding);
      return replaced != null
          ? replaced // We raced another thread and lost.
          : jitBinding;
    }

    return null;
  }

  LinkedBinding<?> replaceLinked(Key key, UnlinkedBinding unlinked, LinkedBinding<?> linked) {
    if (!bindings.replace(key, unlinked, linked)) {
      // If replace() returned false we raced another thread and lost. Return the winner.
      LinkedBinding<?> race = (LinkedBinding<?>) bindings.get(key);
      if (race == null) throw new AssertionError();
      return race;
    }
    return linked;
  }

  static final class Builder {
    private final Map<Key, Binding> bindings = new LinkedHashMap<>();
    private final Map<Key, List<Binding>> setBindings = new LinkedHashMap<>();
    private final Map<Key, Map<Object, Binding>> mapBindings = new LinkedHashMap<>();
    private JustInTimeProvider jitProvider = JustInTimeProvider.NONE;

    Builder justInTimeProvider(JustInTimeProvider jitProvider) {
      if (jitProvider == null) throw new NullPointerException("jitProvider == null");
      this.jitProvider = jitProvider;
      return this;
    }

    Builder add(Key key, Binding binding) {
      if (key == null) throw new NullPointerException("key == null");
      if (binding == null) throw new NullPointerException("binding == null");

      Binding replaced = bindings.put(key, binding);
      if (replaced != null) {
        throw new IllegalStateException(
            "Duplicate binding for " + key + ": " + replaced + " and " + binding);
      }
      return this;
    }

    Builder addIntoSet(Key key, Binding binding) {
      if (key == null) throw new NullPointerException("key == null");
      if (binding == null) throw new NullPointerException("binding == null");

      List<Binding> bindings = setBindings.get(key);
      if (bindings == null) {
        bindings = new ArrayList<>();
        setBindings.put(key, bindings);
      }
      bindings.add(binding);
      return this;
    }

    /**
     * Adds a new entry into the map specified by {@code key}.
     *
     * @param key The key defining the map in which this entry will be added. The raw class of the
     * {@linkplain Key#type() type} must be {@link Map Map.class}.
     * @param entryKey The key of the new map entry. The argument must be an instance of the
     * {@code K} type parameter of {@link Map} specified
     * {@linkplain Key#type() in the <code>key</code>}.
     * @param entryValueBinding The value binding of the new map entry. The instance produced by
     * this binding must be an instance of the {@code V} type parameter of {@link Map} specified
     * {@linkplain Key#type()} in the <code>key</code>}.
     */
    Builder addIntoMap(Key key, Object entryKey, Binding entryValueBinding) {
      if (key == null) throw new NullPointerException("key == null");
      // TODO sanity check raw type of key is Map?
      if (entryKey == null) throw new NullPointerException("entryKey == null");
      if (entryValueBinding == null) throw new NullPointerException("entryValueBinding == null");

      Map<Object, Binding> mapBinding = mapBindings.get(key);
      if (mapBinding == null) {
        mapBinding = new LinkedHashMap<>();
        mapBindings.put(key, mapBinding);
      }
      Binding replaced = mapBinding.put(entryKey, entryValueBinding);
      if (replaced != null) {
        throw new IllegalStateException(); // TODO duplicate keys
      }
      return this;
    }

    BindingMap build() {
      ConcurrentHashMap<Key, Binding> allBindings = new ConcurrentHashMap<>(bindings);

      // Coalesce all of the bindings for each key into a single set binding.
      for (Map.Entry<Key, List<Binding>> setBinding : setBindings.entrySet()) {
        Key elementKey = setBinding.getKey();
        Key setKey = Key.of(elementKey.qualifier(),
            new ParameterizedTypeImpl(null, Set.class, elementKey.type()));

        // Take a defensive copy in case the builder is being re-used.
        List<Binding> elementBindings = new ArrayList<>(setBinding.getValue());

        Binding replaced = allBindings.put(setKey, new UnlinkedSetBinding(elementBindings));
        if (replaced != null) {
          throw new IllegalStateException(); // TODO implicit set binding duplicates explicit one.
        }
      }

      // Coalesce all of the bindings for each key into a single map binding.
      for (Map.Entry<Key, Map<Object, Binding>> mapBinding : mapBindings.entrySet()) {
        Key key = mapBinding.getKey();

        // Take a defensive copy in case the builder is being re-used.
        Map<Object, Binding> entryBindings = new LinkedHashMap<>(mapBinding.getValue());

        Binding replaced = allBindings.put(key, new UnlinkedMapBinding(entryBindings));
        if (replaced != null) {
          throw new IllegalStateException(); // TODO implicit map binding duplicates explicit one.
        }
      }

      return new BindingMap(allBindings, jitProvider);
    }
  }

  interface JustInTimeProvider {
    @Nullable UnlinkedBinding create(Key key);

    JustInTimeProvider NONE = key -> null;
  }
}