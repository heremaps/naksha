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
import naksha.base.TupleNumber;
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
  private final @NotNull Id databaseId;
  private final @NotNull Id mapId;
  private final @NotNull Id collectionId;

  /**
   * Create a new view-layer.
   * @param storage the storage to which to redirect requests.
   * @param mapId the map-id of the map to which to redirect requests _(added in v3)_.
   * @param collectionId the collection-id of the collection to which to redirect requests.
   * @since 2.0
   */
  public ViewLayer(@NotNull IStorage storage, @Nullable Id databaseId, @NotNull Id mapId, @NotNull Id collectionId) {
    this.storage = storage;
    this.databaseId = databaseId != null ? databaseId : storage.getDefaultDatabaseId();
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
    this(storage, collection.getDatabaseId(), collection.getCatalogId(), collection.getId());
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
   * Return the `id` of the database.
   * @return the `id` of the database.
   * @since 3.0
   */
  public @NotNull Id getDatabaseId() {
    return databaseId;
  }

  /**
   * Returns the map-id to which this layer redirects.
   * @return the map-id of the map to which to redirect requests.
   * @since 2.0
   */
  public @NotNull Id getMapId() {
    return mapId;
  }

  /**
   * Returns the collection-id to which this layer redirects.
   * @return the collection-id of the map to which to redirect requests.
   * @since 2.0
   */
  public @NotNull Id getCollectionId() {
    return collectionId;
  }

  /**
   * Tests if a feature with the given tuple-number can be found in this layer.
   * @param tupleNumber the tuple-number to test
   * @return true if the feature can be found in this layer; false otherwise.
   */
  public boolean contains(@Nullable TupleNumber tupleNumber) {
    return tupleNumber!= null
        && databaseId.getNumber() == tupleNumber.databaseNumber
        && mapId.getIntValue() == tupleNumber.catalogNumber
        && collectionId.getIntValue() == tupleNumber.collectionNumber;
  }
}
