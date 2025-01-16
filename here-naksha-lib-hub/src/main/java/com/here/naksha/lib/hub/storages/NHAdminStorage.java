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
package com.here.naksha.lib.hub.storages;

import java.util.Map;
import naksha.base.Int64;
import naksha.model.ILock;
import naksha.model.IMap;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.NakshaVersion;
import naksha.model.SessionOptions;
import naksha.model.Tuple;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NHAdminStorage implements IStorage {

  public static final String ID_PREFIX = "NH_ADMIN_STORAGE_";

  /**
   * Singleton instance of physical admin storage implementation
   */
  private final @NotNull IStorage psqlStorage;

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public NHAdminStorage(final @NotNull IStorage psqlStorage) {
    this.psqlStorage = psqlStorage;
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public void initStorage(@Nullable Map<String, ?> params) {
    this.psqlStorage.initStorage(params);
  }

  @NotNull
  @Override
  public IWriteSession newWriteSession(@Nullable SessionOptions options) {
    return new NHAdminStorageWriter(psqlStorage.newWriteSession(options));
  }

  @NotNull
  @Override
  public IReadSession newReadSession(@Nullable SessionOptions options) {
    return new NHAdminStorageReader(psqlStorage.newReadSession(options));
  }

  @Override
  public void close() {
    psqlStorage.close();
  }

  @NotNull
  @Override
  public String getId() {
    return ID_PREFIX + psqlStorage.getId();
  }

  @NotNull
  @Override
  public SessionOptions getAdminOptions() {
    return psqlStorage.getAdminOptions();
  }

  @Override
  public int getHardCap() {
    return psqlStorage.getHardCap();
  }

  @Override
  public void setHardCap(int i) {
    psqlStorage.setHardCap(i);
  }

  @Override
  public boolean isInitialized() {
    return psqlStorage.isInitialized();
  }

  @NotNull
  @Override
  public IMap getDefaultMap() {
    return psqlStorage.getDefaultMap();
  }

  @NotNull
  @Override
  public IMap get(@NotNull String mapId) {
    return psqlStorage.get(mapId);
  }

  @Nullable
  @Override
  public IMap get(int mapNumber) {
    return psqlStorage.get(mapNumber);
  }

  @Override
  public boolean contains(@NotNull String mapId) {
    return psqlStorage.contains(mapId);
  }

  @Nullable
  @Override
  public String getMapId(int mapNumber) {
    return psqlStorage.getMapId(mapNumber);
  }

  @NotNull
  @Override
  public NakshaFeature tupleToFeature(@NotNull Tuple tuple) {
    return psqlStorage.tupleToFeature(tuple);
  }

  @NotNull
  @Override
  public Tuple featureToTuple(@NotNull NakshaFeature feature) {
    return psqlStorage.featureToTuple(feature);
  }

  @NotNull
  @Override
  @Deprecated
  public ILock enterLock(@NotNull String id, @NotNull Int64 waitMillis) {
    throw new NakshaException(new NakshaError(NakshaError.NOT_IMPLEMENTED, "enterLock not supported"));
  }
}
