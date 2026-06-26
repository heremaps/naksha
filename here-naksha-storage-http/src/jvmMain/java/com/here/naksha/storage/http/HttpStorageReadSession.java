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
import com.here.naksha.storage.http.connector.ConnectorInterfaceReadExecute;
import com.here.naksha.storage.http.ffw.FfwInterfaceReadExecute;
import naksha.model.IReadSession;
import naksha.model.MemberProcessorMap;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaCatalog;
import naksha.model.request.ErrorResponse;
import naksha.model.request.FeatureTuple;
import naksha.model.request.Request;
import naksha.model.request.Response;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static naksha.model.LibModelKt.FETCH_ALL;

public class HttpStorageReadSession implements IReadSession {

  private static final Logger log = LoggerFactory.getLogger(HttpStorageReadSession.class);

  @NotNull
  private final NakshaContext context;

  @NotNull
  private final RequestSender requestSender;

  @NotNull
  private final HttpInterface httpInterface;

  HttpStorageReadSession(@Nullable NakshaContext context, @NotNull RequestSender requestSender, @NotNull HttpInterface httpInterface) {
    this.context = context == null ? NakshaContext.currentContext() : context;
    this.requestSender = requestSender;
    this.httpInterface = httpInterface;
  }

  public @NotNull NakshaContext getNakshaContext() {
    return context;
  }

  @Override
  public @NotNull Response execute(@NotNull Request readRequest) {
    try {
      switch (httpInterface) {
        case ffwAdapter:
          return FfwInterfaceReadExecute.execute(
              context, (ReadFeaturesProxyWrapper) readRequest, requestSender);
        case dataHubConnector:
          return ConnectorInterfaceReadExecute.execute(
              context, (ReadFeaturesProxyWrapper) readRequest, requestSender);
        default:
          throw new IllegalStateException("Unsupported HTTP interface: " + httpInterface);
      }
    } catch (Exception exception) {
      log.warn("We got exception while executing Read request.", exception);
      return new ErrorResponse(NakshaError.EXCEPTION, exception.getMessage(), exception);
    }
  }

  @Override
  public void close() {}

  @Override
  public int getSocketTimeout() {
    return requestSender.keyProps.getSocketTimeoutSec();
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
  public @Nullable NakshaCatalog getCatalogById(@NotNull String catalogId) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaCatalog getCatalogByNumber(int catalogNumber) {
    return null;
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples, int from, int to, int mode) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaCollection getCollectionByNumber(@NotNull NakshaCatalog catalog, int collectionNumber) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @NotNull SessionOptions getOptions() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @NotNull MemberProcessorMap getProcessors() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable NakshaCollection getCollectionById(@NotNull NakshaCatalog map, @NotNull String collectionId) {
    // TODO: Technically, this translates into creating an ReadCollections query!
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples) {
    loadTuples(featureTuples, 0, featureTuples.size(), FETCH_ALL);
  }

  @NotNull
  RequestSender getRequestSender() {
    return requestSender;
  }
}
