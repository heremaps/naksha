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
package com.here.naksha.lib.core.util;

import naksha.base.StringList;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  public static @NotNull StringList hubSlimIndices() {
    return StringList.of("id", "tags", "gist_geo", "next_version");
  }

  public static @NotNull NakshaCollection hubSlimCollection(
      final @NotNull String collectionId,
      final @NotNull String mapId) {
    return normalizeForHubCreation(null, collectionId, mapId);
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @Nullable NakshaCollection collection,
      final @NotNull String collectionId,
      final @NotNull String mapId) {
    final NakshaCollection normalized = collection == null ? new NakshaCollection() : collection.copy(true);
    normalized.setId(collectionId);
    normalized.setMapId(mapId);
    if (normalized.getIndices() == null) {
      normalized.setIndices(hubSlimIndices());
    }
    return normalized;
  }
}
