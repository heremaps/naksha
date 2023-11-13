/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.lib.core.models.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.lib.core.NakshaVersion;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * A request to modify features into a collection of the storage.
 *
 * @param <T> the feature-type to write.
 */
@AvailableSince(NakshaVersion.v2_0_7)
public class WriteFeatures<T> extends WriteRequest<T, WriteFeatures<T>> {

  /**
   * Creates a new empty feature write request.
   *
   * @param collectionId the identifier of the collection to write into.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public WriteFeatures(@NotNull String collectionId) {
    this(collectionId, new ArrayList<>());
  }

  /**
   * Creates a new feature write request.
   *
   * @param collectionId the identifier of the collection to write into.
   * @param modifies     the operations to execute.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public WriteFeatures(@NotNull String collectionId, @NotNull List<WriteOp<T>> modifies) {
    super(modifies);
    this.collectionId = collectionId;
  }

  /**
   * Returns the collection-id to write features into.
   *
   * @return the collection-id to write features into .
   */
  public @NotNull String getCollectionId() {
    return collectionId;
  }

  /**
   * Sets the collection-id to write features into.
   *
   * @param collectionId the collection-id to write features into.
   */
  public void setCollectionId(@NotNull String collectionId) {
    this.collectionId = collectionId;
  }

  /**
   * Sets the collection-id to write features into.
   *
   * @param collectionId the collection-id to write features into.
   * @return this.
   */
  public @NotNull WriteFeatures<T> withCollectionId(@NotNull String collectionId) {
    this.collectionId = collectionId;
    return this;
  }

  /**
   * The identifier of the collection to write into.
   */
  @JsonProperty
  @AvailableSince(NakshaVersion.v2_0_7)
  private @NotNull String collectionId;
}
