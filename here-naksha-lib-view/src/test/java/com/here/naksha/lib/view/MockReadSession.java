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

import naksha.model.request.Request;
import naksha.model.request.ResultRow;
import naksha.model.response.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockReadSession implements IReadSession {

  List<ResultRow> results;

  public MockReadSession(List<ResultRow> results) {
    this.results = results;
  }

  @Override
  public void close() {}

  @NotNull
  @Override
  public Response execute(@NotNull Request<?> request) {
    return null;
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request<?> request) {
    return null;
  }

  @Nullable
  @Override
  public ResultRow getFeatureById(@NotNull String id) {
    return null;
  }

  @NotNull
  @Override
  public Map<String, ResultRow> getFeaturesByIds(@NotNull List<String> ids) {
    return Map.of();
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

  @NotNull
  @Override
  public String getRealm() {
    return "";
  }

  @Override
  public void setRealm(@NotNull String s) {

  }

  @Override
  public boolean isClosed() {
    return false;
  }
}
