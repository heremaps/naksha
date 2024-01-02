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
package com.here.naksha.lib.core.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.util.StringHelper;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * A collection is a virtual container for features, managed by a {@link Storage}. All collections optionally have a history and transaction
 * log.
 */
@Deprecated
@AvailableSince(NakshaVersion.v2_0_3)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectionInfo {

  // Note: Meta information attached JSON serialized to collection tables in PostgresQL.
  //       COMMENT ON TABLE test IS 'Some table';
  //       SELECT pg_catalog.obj_description('test'::regclass, 'pg_class');
  //       Comments can also be added on other objects, like columns, data types, functions, etc.
  //       SELECT obj_description(oid) FROM pg_class WHERE relkind = 'r'
  // See:
  // https://stackoverflow.com/questions/17947274/is-it-possible-to-add-table-metadata-in-postgresql

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String ID = "id";

  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String NUMBER = "number";

  /**
   * Create a new empty collection.
   *
   * @param id            The identifier of the collection.
   * @param storageNumber The storage number, a 40-but unsigned integer generated by the Naksha-Hub. The value zero is reserved for Hub
   *                      internal collections.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonCreator
  public CollectionInfo(@JsonProperty(ID) @NotNull String id, @JsonProperty(NUMBER) long storageNumber) {
    this.id = id;
    this.number = storageNumber;
  }

  /**
   * Create a collection reference with an invalid number (minus one).
   *
   * @param id the identifier of the collection.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  public CollectionInfo(@NotNull String id) {
    this.id = id;
    this.number = -1L;
  }

  /**
   * The identifier of the collection.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  @JsonProperty(ID)
  private @NotNull String id;

  /**
   * The unique storage identifier in which this collection is stored, being a 40-bit unsigned integer.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(NUMBER)
  private long number;

  /**
   * The maximum age of the history entries in days. Zero means no history, {@link Long#MAX_VALUE} means unlimited.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  private long maxAge = Long.MAX_VALUE;

  /**
   * Toggle if the history is enabled.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  private boolean history = Boolean.TRUE;

  /**
   * A value greater than zero implies that the collection shall be treated as deleted and represents the UTC Epoch timestamp in
   * milliseconds when the deletion has been done.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonInclude(Include.NON_DEFAULT)
  private long deletedAt = 0L;

  /**
   * Returns the collection identifier.
   *
   * @return the collection identifier.
   */
  public @NotNull String getId() {
    return id;
  }

  /**
   * Set the collection identifier.
   *
   * @param id the collection identifier to set.
   */
  public void setId(@NotNull String id) {
    this.id = id;
  }

  /**
   * Returns the storage number, a 40-bit unsigned integer.
   *
   * @return the storage number, a 40-bit unsigned integer.
   */
  public long getNumber() {
    return number;
  }

  /**
   * Set the storage number, a 40-bit unsigned integer normally only provided by the Naksha-Hub.
   *
   * @param number the storage number to set.
   */
  public void setNumber(long number) {
    this.number = number;
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
  public boolean getHistory() {
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
  public long getDeletedAt() {
    return deletedAt;
  }

  /**
   * Sets the UNIX epoch time in milliseconds when to delete the collection; zero or less when the collection should never be deleted.
   *
   * @param deletedAt the UNIX epoch time in milliseconds when to delete the collection; zero or less when the collection should never be
   *                  deleted.
   */
  public void setDeletedAt(long deletedAt) {
    this.deletedAt = deletedAt;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other instanceof CollectionInfo) {
      CollectionInfo otherInfo = (CollectionInfo) other;
      return number == otherInfo.number && id.equals(otherInfo.id);
    }
    if (other instanceof CharSequence) {
      CharSequence chars = (CharSequence) other;
      return StringHelper.equals(id, chars);
    }
    return false;
  }

  @Override
  public String toString() {
    return id + ":" + number;
  }
}
