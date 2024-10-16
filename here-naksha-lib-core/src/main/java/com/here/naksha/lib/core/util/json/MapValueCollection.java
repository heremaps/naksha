/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.core.util.json;

import static com.here.naksha.lib.core.util.FibMap.EMPTY;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Core implementation of a map-value collection. */
public class MapValueCollection<K, V> implements Collection<@Nullable V> {

  public MapValueCollection(@NotNull Map<K, V> map) {
    this.map = map;
  }

  @JsonIgnore
  protected final @NotNull Map<K, V> map;

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    //noinspection SuspiciousMethodCalls
    return map.containsValue(o);
  }

  @Nonnull
  @Override
  public Iterator<@Nullable V> iterator() {
    final Iterator<Entry<@NotNull K, @Nullable V>> it = map.entrySet().iterator();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public V next() {
        return it.next().getValue();
      }
    };
  }

  @Override
  public @Nullable Object @NotNull [] toArray() {
    return toArray(EMPTY);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> @Nullable T @NotNull [] toArray(@Nullable T @NotNull [] original) {
    return (T[]) JsonUtils.mapToArray(map, original, JsonUtils::extractValue);
  }

  @Override
  public boolean add(@Nullable Object o) {
    throw new UnsupportedOperationException("can't add values without key");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("can't remove values without key");
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException("can't find values without key");
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends V> c) {
    throw new UnsupportedOperationException("can't add values without key");
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException("can't remove values without key");
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException("can't find values without key");
  }

  @Override
  public void clear() {
    map.clear();
  }
}
