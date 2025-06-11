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

import java.util.List;

import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.NakshaVersion;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaMap;
import naksha.model.request.FeatureTuple;
import naksha.model.request.Request;
import naksha.model.request.Response;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.model.LibModelKt.FETCH_ALL;

public class NHAdminStorageReader implements IReadSession {

  /**
   * Current session, all read storage operations should be executed against
   */
  final @NotNull IReadSession session;

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  protected NHAdminStorageReader(final @NotNull IReadSession reader) {
    this.session = reader;
  }

  @Override
  public int getSocketTimeout() {
    return session.getSocketTimeout();
  }

  @Override
  public void setSocketTimeout(int i) {
    session.setSocketTimeout(i);
  }

  @Override
  public int getStmtTimeout() {
    return session.getStmtTimeout();
  }

  @Override
  public void setStmtTimeout(int i) {
    session.setStmtTimeout(i);
  }

  @Override
  public int getLockTimeout() {
    return session.getLockTimeout();
  }

  @Override
  public void setLockTimeout(int i) {
    session.setLockTimeout(i);
  }

  @NotNull
  @Override
  public Response performExecute(@NotNull Request request) {
    return session.execute(request);
  }

  @Override
  public boolean isClosed() {
    return session.isClosed();
  }

  @Override
  public void close() {
    session.close();
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    throw new NakshaException(
        new NakshaError(NakshaError.NOT_IMPLEMENTED, "parallel execution not supported for NHAdmin"));
  }

  @Override
  public @NotNull IStorage getStorage() {
    return session.getStorage();
  }

  @Override
  public @NotNull SessionOptions getOptions() {
    return session.getOptions();
  }

  @Override
  public @Nullable NakshaMap getMapById(@NotNull String mapId) {
    return session.getMapById(mapId);
  }

  @Override
  public @Nullable NakshaMap getMapByNumber(int mapNumber) {
    return session.getMapByNumber(mapNumber);
  }

  @Override
  public @Nullable NakshaCollection getCollectionById(@NotNull NakshaMap map, @NotNull String collectionId) {
    return session.getCollectionById(map, collectionId);
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples, int from, int to, int mode) {
    session.loadTuples(featureTuples, from, to, mode);
  }

  @Override
  public @Nullable NakshaCollection getCollectionByNumber(@NotNull NakshaMap map, int collectionNumber) {
    return session.getCollectionByNumber(map, collectionNumber);
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples) {
    loadTuples(featureTuples, 0, featureTuples.size(), FETCH_ALL);
  }
}
