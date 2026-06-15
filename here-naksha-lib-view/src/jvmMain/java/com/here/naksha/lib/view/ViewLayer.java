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

import naksha.model.IStorage;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view-layer is a virtual collection being part of a view. It redirects data requests to another storage, map, and collection.
 * @since 2.0
 */
public class ViewLayer {

  private final @NotNull IStorage storage;
  private final @Nullable String mapId;
  private final @NotNull String collectionId;

  /**
   * Create a new view-layer.
   * @param storage the storage to which to redirect requests.
   * @param mapId the map-id of the map to which to redirect requests _(added in v3)_.
   * @param collectionId the collection-id of the collection to which to redirect requests.
   * @since 2.0
   */
  public ViewLayer(@NotNull IStorage storage, @Nullable String mapId, @NotNull String collectionId) {
    this.storage = storage;
    this.mapId = mapId;
    this.collectionId = collectionId;
  }

  /**
   * Create a new view-layer.
   * @param storage the storage to which to redirect requests.
   * @param collection the collection to which to redirect requests.
   * @since 2.0
   */
  public ViewLayer(@NotNull IStorage storage, @NotNull NakshaCollection collection) {
    this(storage, collection.getCatalogId(), collection.getId());
  }

  /**
   * Create a new view-layer without a map-id.
   * @param storage the storage to which to redirect requests.
   * @param collectionId the collection-id of the collection to which to redirect requests.
   * @since 3.0
   */
  public ViewLayer(@NotNull IStorage storage, @NotNull String collectionId) {
    this(storage, null, collectionId);
  }

  /**
   * Returns the storage to which this layer redirects.
   * @return the storage to which this layer redirects.
   * @since 2.0
   */
  public @NotNull IStorage getStorage() {
    return storage;
  }

  /**
   * Returns the map-id to which this layer redirects.
   * @return the map-id of the map to which to redirect requests.
   * @since 2.0
   */
  public @Nullable String getMapId() {
    return mapId;
  }

  /**
   * Returns the collection-id to which this layer redirects.
   * @return the collection-id of the map to which to redirect requests.
   * @since 2.0
   */
  public @NotNull String getCollectionId() {
    return collectionId;
  }
}
