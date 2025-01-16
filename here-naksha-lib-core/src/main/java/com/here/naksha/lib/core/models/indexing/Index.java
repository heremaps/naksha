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

import com.here.naksha.lib.core.models.indexing.IndexProperty.IndexProperties;
import java.util.List;
import naksha.base.JvmBoxingUtil;
import naksha.base.JvmMapProxy;
import naksha.model.objects.NakshaFeature;

/** The specification of an index. */
public class Index extends NakshaFeature {

  private static final String ALG = "alg";
  private static final String INDEX_HISTORY = "indexHistory";
  private static final String NESTED_INDEX_PROPS = "index";

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
  public String getAlg() {
    return (String) getRaw(ALG);
  }

  public void setAlg(String alg) {
    setRaw(ALG, alg);
  }

  /** If the index should be applied to the history too. */
  public boolean isIndexHistory() {
    return getOrSet(INDEX_HISTORY, false);
  }

  public void setIndexHistory(boolean indexHistory) {
    setRaw(INDEX_HISTORY, indexHistory);
  }

  /** All properties that should be included in this index. */
  public List<IndexProperty> getIndexProperties() {
    return JvmBoxingUtil.box(getProperties().get(NESTED_INDEX_PROPS), IndexProperties.class);
  }

  public void setProperties(List<IndexProperty> properties) {
    IndexProperties indexProperties = new IndexProperties();
    indexProperties.addAll(properties);
    getProperties().setRaw(NESTED_INDEX_PROPS, indexProperties);
  }

  public static class Map extends JvmMapProxy<String, Index> {
    public Map() {
      super(String.class, Index.class);
    }
  }
}
