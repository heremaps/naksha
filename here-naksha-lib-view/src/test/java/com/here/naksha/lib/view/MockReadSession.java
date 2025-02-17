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

import naksha.jbon.JbDictionary;
import naksha.model.IReadSession;
import java.util.List;

import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.Tuple;
import naksha.model.TupleNumber;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaMap;
import naksha.model.objects.NakshaTransaction;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.FeatureTuple;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockReadSession implements IReadSession {

  List<FeatureTuple> results;

  public MockReadSession(List<FeatureTuple> results) {
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
  public void loadTuples(@NotNull List<? extends FeatureTuple> resultTuples, int from, int to, boolean fetchFromHistory, int mode) {

  }

  @Override
  public @NotNull IStorage getStorage() {
    return null;
  }

  @Override
  public @Nullable NakshaMap getMapById(@NotNull String mapId) {
    return null;
  }

  @Override
  public @Nullable NakshaMap getMapByNumber(int mapNumber) {
    return null;
  }

  @Override
  public void refreshMaps() {

  }

  @Override
  public @Nullable NakshaCollection getCollectionById(@NotNull NakshaMap map, @NotNull String collectionId) {
    return null;
  }

  @Override
  public @Nullable NakshaCollection getCollectionByNumber(@NotNull NakshaMap map, int collectionNumber) {
    return null;
  }

  @Override
  public void refreshCollections(@NotNull NakshaMap map) {

  }

  @Override
  public int getEncodingFlags(@Nullable Object feature, @Nullable Object context) {
    return 0;
  }

  @Override
  public @Nullable JbDictionary getDictionary(@NotNull String id) {
    return null;
  }

  @Override
  public @Nullable JbDictionary getEncodingDictionary(@Nullable Object feature, @Nullable Object context) {
    return null;
  }

  @Override
  public @NotNull SessionOptions getOptions() {
    return null;
  }
}
