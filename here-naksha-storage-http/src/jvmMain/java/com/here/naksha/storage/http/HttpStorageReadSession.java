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

import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import java.util.List;

import naksha.base.NakshaError;
import naksha.model.*;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaMap;
import naksha.model.request.ErrorResponse;
import naksha.model.request.FeatureTuple;
import naksha.model.request.Request;
import naksha.model.request.Response;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static naksha.model.LibModelKt.FETCH_ALL;

public final class HttpStorageReadSession implements IReadSession {

  private static final Logger log = LoggerFactory.getLogger(HttpStorageReadSession.class);

  @NotNull
  private final NakshaContext context;

  @NotNull
  private final RequestSender requestSender;

  HttpStorageReadSession(@Nullable NakshaContext context, @NotNull RequestSender requestSender) {
    this.context = context == null ? NakshaContext.currentContext() : context;
    this.requestSender = requestSender;
  }

  public @NotNull NakshaContext getNakshaContext() {
    return context;
  }

  @Override
  public @NotNull Response execute(@NotNull Request readRequest) {
    try {
      return HttpStorageReadExecute.execute(context, (ReadFeaturesProxyWrapper) readRequest, requestSender);
    } catch (Exception exception) {
      log.warn("We got exception while executing Read request.", exception);
      return new ErrorResponse(NakshaError.EXCEPTION, exception.getMessage(), exception);
    }
  }

  @Override
  public void close() {}

  @Override
  public int getSocketTimeout() {
    return requestSender.keyProps.socketTimeoutSec();
  }

  @Override
  public void setSocketTimeout(int i) {
    throw new IllegalStateException("Can only be set when creating the session");
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

  @Override
  public boolean isClosed() {
    return false;
    // TODO
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return execute(request);
  }


  @Override
  public @NotNull IStorage getStorage() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaMap getMapById(@NotNull String mapId) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaMap getMapByNumber(int mapNumber) {
    return null;
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples, int from, int to, int mode) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaCollection getCollectionByNumber(@NotNull NakshaMap map, int collectionNumber) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @NotNull SessionOptions getOptions() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaCollection getCollectionById(@NotNull NakshaMap map, @NotNull String collectionId) {
    // TODO: Technically, this translates into creating an ReadCollections query!
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples) {
    loadTuples(featureTuples, 0, featureTuples.size(), FETCH_ALL);
  }
}
