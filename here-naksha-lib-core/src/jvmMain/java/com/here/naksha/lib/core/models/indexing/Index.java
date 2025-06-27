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
package com.here.naksha.lib.core.models.indexing;

import com.here.naksha.lib.core.models.indexing.IndexProperty.IndexPropertyList;
import java.util.List;
import naksha.base.MapProxy;
import naksha.base.NotNullProperty;
import naksha.base.NullableProperty;
import naksha.base.PlatformType;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.Boolean_TYPE;
import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

/** The specification of an index. */
public class Index extends NakshaFeature {
  public static final PlatformType<Index> TYPE = forClass(Index.class);

  private static final NullableProperty<Index, String> ALG
      = new NullableProperty<>(String_TYPE, "alg");
  private static final NotNullProperty<Index, Boolean> INDEX_HISTORY
      = new NotNullProperty<>(Boolean_TYPE, "indexHistory", (self, name) -> Boolean.FALSE);
  private static final NullableProperty<Index, String> INDEX
      = new NullableProperty<>(String_TYPE, "index");

  /**
   * The algorithm to use. The implementing processor will decide if it supports the algorithm.
   *
   * <p>The PostgresQL processor supports the following algorithms (with its recommended targets):
   * <ul>
   * <li>{@code btree} for {@code String}, {@code Number} and {@code Boolean}.
   * <li>{@code hash} for {@code String}, {@code Number} and {@code Boolean}.
   * <li>{@code brin} for {@code String}, {@code Number} and {@code Boolean}.
   * <li>{@code gin} for {@code List} and {@code Map}.
   * <li>{@code gin_trigram} for {@code String}.
   * </ul>
   * <p>Note that if no algorithm given, the PostgresQL processor will auto-select on and return the selected algorithm in the response.
   */
  public @Nullable String getAlg() {
    return ALG.getValue(this);
  }

  public void setAlg(@Nullable String alg) {
    ALG.setValue(this, alg);
  }

  /** If the index should be applied to the history too. */
  public boolean isIndexHistory() {
    return INDEX_HISTORY.getValue(this);
  }

  public void setIndexHistory(boolean indexHistory) {
    INDEX_HISTORY.setValue(this, indexHistory);
  }

  /** All properties that should be included in this index. */
  public @Nullable IndexProperty.IndexPropertyList getIndexProperties() {
    return getProperties().getAs(NESTED_INDEX_PROPS, IndexPropertyList.TYPE);
  }

  public void setProperties(List<IndexProperty> properties) {
    IndexPropertyList indexProperties = new IndexPropertyList();
    indexProperties.addAll(properties);
    getProperties().setRaw(NESTED_INDEX_PROPS, indexProperties);
  }

  public static class Map extends MapProxy<String, Index> {
    public Map() {
      super(forClass(String.class), forClass(Index.class));
    }
  }
}
