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
package com.here.naksha.lib.core.models.storage;

import static com.here.naksha.lib.core.models.naksha.Storage.NUMBER;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.naksha.NakshaFeature;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.storage.CollectionInfo;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * A collection is a virtual container for features, managed by a {@link Storage}. All collections optionally have a history and transaction
 * log.
 *
 * @deprecated Please use {@link XyzCollection}.
 */
@Deprecated
@JsonTypeName(value = "StorageCollection")
@AvailableSince(NakshaVersion.v2_0_3)
public class StorageCollection extends NakshaFeature {

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String MAX_AGE = "maxAge";

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String HISTORY = "history";

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String DELETED_AT = "deleted";

  /**
   * Create a new empty collection.
   *
   * @param id the identifier of the collection.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonCreator
  public StorageCollection(@JsonProperty(ID) @NotNull String id) {
    super(id);
  }

  /**
   * Constructor to wrap a collection information as returned by a storage into an feature.
   *
   * @param info the collection information.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  @Deprecated
  public StorageCollection(@NotNull CollectionInfo info) {
    super(info.getId());
    this.number = info.getNumber();
    this.history = info.getHistory();
    this.deletedAt = info.getDeletedAt();
    this.maxAge = info.getMaxAge();
  }

  /**
   * The unique storage identifier, being a 40-bit unsigned integer.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(NUMBER)
  @Deprecated
  private long number;

  /**
   * The maximum age of the history entries in days. Zero means no history, {@link Long#MAX_VALUE} means unlimited.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(MAX_AGE)
  private long maxAge = Long.MAX_VALUE;

  /**
   * Toggle if the history is enabled.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(HISTORY)
  private boolean history = Boolean.TRUE;

  /**
   * A value greater than zero implies that the collection shall be treated as deleted and represents the UTC Epoch timestamp in
   * milliseconds when the deletion has been done.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(DELETED_AT)
  @JsonInclude(Include.NON_DEFAULT)
  private long deletedAt = 0L;

  /**
   * Returns the storage number, a 40-bit unsigned integer.
   *
   * @return the storage number, a 40-bit unsigned integer.
   */
  @Deprecated
  public long getNumber() {
    return number;
  }

  /**
   * Returns the maximum age of the storage collection history in days.
   *
   * @return the maximum age of the storage collection history in days.
   */
  public long getMaxAge() {
    return maxAge;
  }

  /**
   * Sets the maximum age of the storage collection history in days.
   *
   * @param maxAge the maximum age of the storage collection history in days.
   */
  public void setMaxAge(long maxAge) {
    this.maxAge = maxAge;
  }

  /**
   * Returns true if the history is currently enabled; false otherwise.
   *
   * @return true if the history is currently enabled; false otherwise.
   */
  public boolean hasHistory() {
    return history;
  }

  /**
   * Enable or disable the history.
   *
   * @param history true to enable the history; false to disable it.
   */
  public void setHistory(boolean history) {
    this.history = history;
  }

  /**
   * Returns the UNIX epoch time in milliseconds when to delete the collection; zero or less when the collection has no deletion time.
   *
   * @return the UNIX epoch time in milliseconds when to delete the collection; zero or less when the collection has no deletion time.
   */
  @Deprecated
  public long getDeletedAt() {
    return deletedAt;
  }

  /**
   * Sets the UNIX epoch time in milliseconds when to delete the collection; zero or less when the collection should never be deleted.
   *
   * @param deletedAt the UNIX epoch time in milliseconds when to delete the collection; zero or less when the collection should never be
   *                  deleted.
   */
  @Deprecated
  public void setDeletedAt(long deletedAt) {
    this.deletedAt = deletedAt;
  }
}
