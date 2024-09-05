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
package com.here.naksha.storage.http;

import com.here.naksha.lib.core.models.storage.*;
import java.util.List;
import naksha.model.*;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.ResultTuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpStorageReadSession implements IReadSession {

  private static final Logger log = LoggerFactory.getLogger(HttpStorageReadSession.class);

  @NotNull
  private final NakshaContext context;

  private final boolean useMaster;

  @NotNull
  private final RequestSender requestSender;

  HttpStorageReadSession(@Nullable NakshaContext context, boolean useMaster, @NotNull RequestSender requestSender) {
    this.context = context == null ? NakshaContext.currentContext() : context;
    this.useMaster = useMaster;
    this.requestSender = requestSender;
  }

  public @NotNull NakshaContext getNakshaContext() {
    return context;
  }

  @Override
  public @NotNull Response execute(@NotNull Request readRequest) {
    try {
      return HttpStorageReadExecute.execute(context, (ReadFeaturesProxyWrapper) readRequest, requestSender);
    } catch (Exception e) {
      log.warn("We got exception while executing Read request.", e);
      return new ErrorResponse(NakshaError.EXCEPTION, e.getMessage(), null, e);
    }
  }

  @Override
  public void close() {}

  @Override
  public int getSocketTimeout() {
    return 0;
  }

  @Override
  public void setSocketTimeout(int i) {}

  @Override
  public int getStmtTimeout() {
    return 0;
  }

  @Override
  public void setStmtTimeout(int i) {}

  @Override
  public int getLockTimeout() {
    return 0;
  }

  @Override
  public void setLockTimeout(int i) {}

  @NotNull
  @Override
  public String getMap() {
    return "";
  }

  @Override
  public void setMap(@NotNull String s) {}

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public boolean validateHandle(@NotNull String handle, @Nullable Integer ttl) {
    return false;
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return IReadSession.super.executeParallel(request);
  }

  @NotNull
  @Override
  public List<Tuple> getLatestTuples(
      @NotNull String mapId,
      @NotNull String collectionId,
      @NotNull String[] featureIds,
      @NotNull FetchMode mode) {
    return List.of();
  }

  @NotNull
  @Override
  public List<Tuple> getTuples(@NotNull TupleNumber[] tupleNumbers, @NotNull FetchMode mode) {
    return List.of();
  }

  @Override
  public void fetchTuple(@NotNull ResultTuple resultTuple, @NotNull FetchMode mode) {}

  @Override
  public void fetchTuples(
      @NotNull List<? extends ResultTuple> resultTuples, int from, int to, @NotNull FetchMode mode) {}
}
