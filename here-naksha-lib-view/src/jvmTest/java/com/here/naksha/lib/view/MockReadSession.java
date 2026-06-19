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
package com.here.naksha.lib.view;

import naksha.model.IReadSession;
import java.util.List;

import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaCatalog;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.model.LibModelKt.FETCH_ALL;

public class MockReadSession implements IReadSession {

  FeatureTupleList results;

  public MockReadSession(FeatureTupleList results) {
    this.results = results;
  }

  @Override
  public void close() {}

  @NotNull
  @Override
  public Response execute(@NotNull Request request) {
    return new SuccessResponse(results);
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return null;
  }

  private int socketTimeout = 0;

  @Override
  public int getSocketTimeout() {
    return socketTimeout;
  }

  @Override
  public void setSocketTimeout(int i) {
    socketTimeout = i;
  }

  private int stmtTimeout = 0;

  @Override
  public int getStmtTimeout() {
    return stmtTimeout;
  }

  @Override
  public void setStmtTimeout(int i) {
    stmtTimeout = i;
  }

  private int lockTimeout = 0;

  @Override
  public int getLockTimeout() {
    return lockTimeout;
  }

  @Override
  public void setLockTimeout(int i) {
    lockTimeout = i;
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  private String map = "";

  @Override
  public @NotNull IStorage getStorage() {
    return null;
  }

  @Override
  public @Nullable NakshaCatalog getMapById(@NotNull String mapId) {
    return null;
  }

  @Override
  public @Nullable NakshaCatalog getCatalogByNumber(int catalogNumber) {
    return null;
  }

  @Override
  public @Nullable NakshaCollection getCollectionById(@NotNull NakshaCatalog map, @NotNull String collectionId) {
    return null;
  }

  @Override
  public @Nullable NakshaCollection getCollectionByNumber(@NotNull NakshaCatalog catalog, int collectionNumber) {
    return null;
  }

  @Override
  public @NotNull SessionOptions getOptions() {
    return null;
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples, int from, int to, int mode) {

  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples) {
    loadTuples(featureTuples, 0, featureTuples.size(), FETCH_ALL);
  }
}
