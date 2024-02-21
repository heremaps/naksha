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

import static java.net.http.HttpRequest.newBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RequestSender {

  private static final Logger log = LoggerFactory.getLogger(RequestSender.class);
  private final String hostUrl;

  private final HttpClient httpClient;

  public HttpRequest.Builder getBuilder() {
    return builder;
  }

  private final HttpRequest.Builder builder;

  public RequestSender(String hostUrl, Map<String, String> headers, HttpClient httpClient, Duration socketTimeout) {
    this.hostUrl = hostUrl;
    this.builder = newBuilder();
    this.httpClient = httpClient;

    builder.timeout(socketTimeout);
    headers.forEach(builder::header);
  }

  private String baseEndpoint;

  /**
   * Send a request configured based on enclosing {@link HttpStorage}.
   *
   * @param endpoint does not contain host:port part, starts with "/".
   */
  HttpResponse<String> sendRequest(String endpoint) throws IOException, InterruptedException {
    String uri = hostUrl + baseEndpoint + endpoint;
    HttpRequest request = builder.uri(URI.create(uri)).build();

    long startTime = System.currentTimeMillis();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    long executionTime = System.currentTimeMillis() - startTime;
    log.info("Request to {} took {}ms", request.uri(), executionTime);

    return response;
  }

  /**
   * Set base endpoint path that is always appended to HttpStorage specific target host:port
   *
   * @param baseEndpoint does not contain host:port part, starts with "/", does not end with "/".
   */
  public void setBaseEndpoint(String baseEndpoint) {
    this.baseEndpoint = baseEndpoint;
  }
}
