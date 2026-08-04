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
package com.here.naksha.lib.view;

import naksha.base.Id;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the strategy for missing feature in one of the layers.
 *
 * <p>Consider this example:
 * <li>Result from Storage A: [F_1, F_2, F_3, F_4]
 * <li>Result from Storage B: [F_2, F_4]
 * <li>Result from Storage C: [F_3, F_5]
 *
 * <p>The feature {@code F_1} is missing in results from storage {@code B} and {@code C}. The implementation of {@link MissingIdResolver} will receive for example {@code F_1} with {@link ViewLayer} of storage {@code A} and should decide in which layers this feature should be searched.
 *
 * <p>Here are couple examples of possible implementations:
 * <ol>
 * <li>Ignore fetching more.
 * <li>Calculate missing features' IDs and try to fetch them by ID - good when your basic query is i.e. by Bbox
 * <li>Fetch only if feature is missing in specific Storage and query again only that Storage/layer.
 * </ol>
 *
 * <b>Notice:</b> If your query is by IDs only, "fetch missing" query will be ignored regardless of {@link MissingIdResolver} implementation.
 * @since 2.0
 */
public interface MissingIdResolver {

  /**
   * True - turns off fetching missing features by ID.
   * @since 2.0
   */
  boolean skip();

  /**
   * Returns a map between {@link ViewLayer} and missing {@link Id}'s, indicating which identifiers should be fetched from which layer in a second query.
   *
   * @param multipleResults a list of view features, all with the same {@link Id}.
   * @return a map between layer and the {@link Id}'s that are missing in it, <b>and</b> which should be loaded.
   * @since 2.0
   */
  @Nullable MissingIdsByLayer layersToSearch(@NotNull ViewLayerFeatureStack multipleResults);

  /**
   * Returns a map between {@link ViewLayer} and missing {@link Id}'s, indicating which identifiers should be fetched from which layer in a second query.
   * @param resultSet the whole result-set of the view.
   * @since 3.0
   */
  default @NotNull MissingIdsByLayer getAllMissingIdsByLayer(@NotNull ViewLayerFeaturesById resultSet) {
    final var result = new MissingIdsByLayer();
    for (final var entry : resultSet.entrySet()) {
      final ViewLayerFeatureStack features = entry.getValue();
      final MissingIdsByLayer missingIdsByLayer = layersToSearch(features);
      if (missingIdsByLayer == null) continue;
      for (final var e : missingIdsByLayer.entrySet()) {
        final ViewLayer layer = e.getKey();
        final MissingIds missingIds = e.getValue();
        result.getOrCreate(layer).addAllIfAbsent(missingIds);
      }
    }
    return result;
  }
}
