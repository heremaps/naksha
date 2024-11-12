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
import naksha.model.objects.Transaction;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.ResultTuple;
import org.apache.commons.lang3.NotImplementedException;
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
    return requestSender.keyProps.socketTimeoutSec;
  }

  @Override
  public void setSocketTimeout(int i) {
    requestSender.keyProps.socketTimeoutSec = i;
  }

  @Override
  public int getStmtTimeout() {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public void setStmtTimeout(int i) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public int getLockTimeout() {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public void setLockTimeout(int i) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public String getMap() {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public void setMap(@NotNull String s) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public boolean isClosed() {
    return false;
    //TODO
  }

  @Override
  public boolean validateHandle(@NotNull String handle, @Nullable Integer ttl) {
    return false;
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return execute(request);
  }

  @NotNull
  @Override
  public List<Tuple> getTuples(@NotNull TupleNumber[] tupleNumbers, boolean fetchFromHistory, int mode) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public void fetchTuples(
      @NotNull List<? extends ResultTuple> resultTuples, int from, int to, boolean fetchFromHistory, int mode) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public Transaction transaction() {
    throw new NotImplementedException("Not yet supported for HTTP storage");
  }
}
