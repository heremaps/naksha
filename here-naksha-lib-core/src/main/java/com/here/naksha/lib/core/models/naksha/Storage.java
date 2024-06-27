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
package com.here.naksha.lib.core.models.naksha;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.models.PluginCache.getStorageConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.lambdas.Fe1;
import com.here.naksha.lib.core.models.PluginCache;
import naksha.model.IStorage;
import naksha.model.NakshaVersion;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration of a storage. Storages are internally used to access and modify features and collection.
 */
@AvailableSince(NakshaVersion.v2_0_0)
@JsonTypeName(value = "Storage")
public class Storage extends Plugin<IStorage, Storage> {

  @Deprecated
  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String NUMBER = "number";

  /**
   * Create a new storage.
   *
   * @param cla$$ the class that implement the {@link IStorage} API.
   * @param id    the unique identifier of the storage (selected by the user).
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public Storage(@NotNull Class<? extends IStorage> cla$$, @NotNull String id) {
    super(cla$$.getName(), id);
  }

  /**
   * Create a new empty storage.
   *
   * @param className the full qualified name of the class to load for this storage. The class need to implement the {@link IStorage} API.
   * @param id        the unique identifier of the storage (selected by the user).
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @JsonCreator
  public Storage(@JsonProperty(CLASS_NAME) @NotNull String className, @JsonProperty(ID) @NotNull String id) {
    super(className, id);
  }

  /**
   * The unique storage number, being a 40-bit unsigned integer.
   */
  @Deprecated
  @AvailableSince(NakshaVersion.v2_0_0)
  @JsonProperty(NUMBER)
  private long number;

  /**
   * Do not use anymore, please call {@link PluginCache#getStorageConstructor(String, Class)} and create the instance yourself.
   */
  @Deprecated
  @Override
  public @NotNull IStorage newInstance(@NotNull INaksha naksha) {
    Fe1<IStorage, Storage> constructor =
        getStorageConstructor("com.here.naksha.lib.psql.PsqlStorage", Storage.class);
    try {
      return constructor.call(null);
    } catch (Exception e) {
      throw unchecked(e);
    }
  }

  @Deprecated
  public long getNumber() {
    return number;
  }

  @Deprecated
  public void setNumber(long number) {
    this.number = number;
  }
}
