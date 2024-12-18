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
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.NakshaVersion;
import naksha.model.Tuple;
import naksha.model.TupleNumber;
import naksha.model.objects.Transaction;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.ResultTuple;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public String getMap() {
    return session.getMap();
  }

  @Override
  public void setMap(@NotNull String s) {
    session.setMap(s);
  }

  @NotNull
  @Override
  public Response execute(@NotNull Request request) {
    return session.execute(request);
  }

  @Override
  public boolean isClosed() {
    return session.isClosed();
  }

  @Override
  public boolean validateHandle(@NotNull String handle, @Nullable Integer ttl) {
    return session.validateHandle(handle, ttl);
  }

  @NotNull
  @Override
  public List<Tuple> getTuples(@NotNull TupleNumber[] tupleNumbers, boolean fetchFromHistory, int mode) {
    return session.getTuples(tupleNumbers, fetchFromHistory, mode);
  }

  @Override
  public void fetchTuples(
      @NotNull List<? extends ResultTuple> resultTuples, int from, int to, boolean fetchFromHistory, int mode) {
    session.fetchTuples(resultTuples, from, to, fetchFromHistory, mode);
  }

  @NotNull
  @Override
  public Transaction transaction() {
    return session.transaction();
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
}
