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
package com.here.naksha.storage.http;

import static java.net.http.HttpRequest.Builder;
import static java.net.http.HttpRequest.newBuilder;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.storage.IReadSession;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpStorageReadSession implements IReadSession {

  private static final Logger log = LoggerFactory.getLogger(HttpStorageReadSession.class);

  @NotNull
  private final NakshaContext context;

  @NotNull
  private final HttpClient httpStorageClient;

  @NotNull
  private final Builder requestBuilderBase;

  @NotNull
  private final String hostUrl;

  HttpStorageReadSession(
      @Nullable NakshaContext context,
      @NotNull HttpStorageProperties properties,
      @NotNull HttpClient httpStorageClient) {
    this.context = context == null ? NakshaContext.currentContext() : context;
    this.requestBuilderBase = createRequestBuilderBase(properties);
    this.httpStorageClient = httpStorageClient;
    this.hostUrl = properties.getUrl();
  }

  /**
   * Builds builder with set socketTimeout and headers from {@link HttpStorageProperties}
   */
  private Builder createRequestBuilderBase(HttpStorageProperties properties) {
    Builder builder = newBuilder().timeout(Duration.ofSeconds(properties.getSocketTimeout()));
    properties.getHeaders().forEach(builder::header);
    return builder;
  }

  @Override
  public boolean isMasterConnect() {
    return true;
  }

  @Override
  public @NotNull NakshaContext getNakshaContext() {
    return context;
  }

  @Override
  public int getFetchSize() {
    throw new NotImplementedException();
  }

  @Override
  public void setFetchSize(int size) {
    throw new NotImplementedException();
  }

  @Override
  public long getStatementTimeout(@NotNull TimeUnit timeUnit) {
    throw new NotImplementedException();
  }

  @Override
  public void setStatementTimeout(long timeout, @NotNull TimeUnit timeUnit) {
    throw new NotImplementedException();
  }

  @Override
  public long getLockTimeout(@NotNull TimeUnit timeUnit) {
    throw new NotImplementedException();
  }

  @Override
  public void setLockTimeout(long timeout, @NotNull TimeUnit timeUnit) {
    throw new NotImplementedException();
  }

  @Override
  public @NotNull Result execute(@NotNull ReadRequest<?> readRequest) {
    log.info("Hello Naksha! HttpStorageReadSession.execute");
    try {
      HttpRequest putRequest =
          requestBuilderBase.uri(ofEndpoint("/")).GET().build();
      HttpResponse<String> httpResponse =
          httpStorageClient.send(putRequest, HttpResponse.BodyHandlers.ofString());
      log.info(httpResponse.body());
      return new SuccessResult();
    } catch (Exception e) {
      return new ErrorResult(XyzError.EXCEPTION, e.getMessage());
    }
  }

  @Override
  public @NotNull Result process(@NotNull Notification<?> notification) {
    throw new NotImplementedException();
  }

  @Override
  public void close() {
    log.info("Bye Naksha!");
  }

  private URI ofEndpoint(String endpoint) throws URISyntaxException {
    return new URI(hostUrl + endpoint);
  }
}
