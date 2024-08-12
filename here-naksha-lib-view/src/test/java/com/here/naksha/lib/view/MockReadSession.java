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
import java.util.Map;

import naksha.model.Tuple;
import naksha.model.TupleNumber;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.ResultTuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockReadSession implements IReadSession {

  List<NakshaFeature> results;

  public MockReadSession(List<NakshaFeature> results) {
    this.results = results;
  }

  @Override
  public void close() {}

  @NotNull
  @Override
  public Response execute(@NotNull Request request) {
    return null;
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return null;
  }

  @Override
  public int getSocketTimeout() {
    return 0;
  }

  @Override
  public void setSocketTimeout(int i) {

  }

  @Override
  public int getStmtTimeout() {
    return 0;
  }

  @Override
  public void setStmtTimeout(int i) {

  }

  @Override
  public int getLockTimeout() {
    return 0;
  }

  @Override
  public void setLockTimeout(int i) {

  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @NotNull
  @Override
  public String getMap() {
    return "";
  }

  @Override
  public void setMap(@NotNull String s) {

  }

  @Override
  public boolean validateHandle(@NotNull String handle, @Nullable Integer ttl) {
    return false;
  }

  @NotNull
  @Override
  public List<Tuple> getLatestTuples(@NotNull String mapId, @NotNull String collectionId, @NotNull String[] featureIds, @NotNull String mode) {
    return List.of();
  }

  @NotNull
  @Override
  public List<Tuple> getTuples(@NotNull TupleNumber[] tupleNumbers, @NotNull String mode) {
    return List.of();
  }

  @Override
  public void fetchTuple(@NotNull ResultTuple resultTuple, @NotNull String mode) {

  }

  @Override
  public void fetchTuples(@NotNull List<? extends ResultTuple> resultTuples, int from, int to, @NotNull String mode) {

  }
}
