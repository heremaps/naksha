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
package com.here.naksha.app.common;

import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.http.RequestOptions;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NakshaTestWebClient {

  private static final boolean ALLOW_DEBUG_SPECIFIC_TIMEOUT = true;

  private static final Logger logger = LoggerFactory.getLogger(NakshaTestWebClient.class);

  private static final long DEBUG_SOCKET_TIMEOUT_SEC = 300;

  private static final long DEFAULT_NON_DEBUG_SOCKET_TIMEOUT_SEC = 5;

  private static final int MAX_CONNECTIONS = 50;

  private static Vertx vertx;

  private final Duration SOCKET_TIMEOUT;
  private final String host;
  private final int port;
  private final String basePath;
  private final HttpClient httpClient;

  public NakshaTestWebClient(final @NotNull String nhUrl, final long connectTimeoutSec, final long socketTimeoutSec) {
    SOCKET_TIMEOUT = Duration.ofSeconds(socketTimeoutSec);
    final URI baseUri = URI.create(nhUrl);
    final boolean ssl = "https".equalsIgnoreCase(baseUri.getScheme());
    host = baseUri.getHost();
    port = baseUri.getPort() != -1 ? baseUri.getPort() : (ssl ? 443 : 80);
    basePath = (baseUri.getRawPath() == null || baseUri.getRawPath().isEmpty()) ? "/" : baseUri.getRawPath();
    final HttpClientOptions clientOptions = new HttpClientOptions()
        .setSsl(ssl)
        .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(connectTimeoutSec))
        .setMaxHeaderSize(32 * 1024);
    final PoolOptions poolOptions = new PoolOptions().setHttp1MaxSize(MAX_CONNECTIONS);
    httpClient = sharedVertx().createHttpClient(clientOptions, poolOptions);
  }

  public NakshaTestWebClient(long socketTimeoutSec) {
    this("http://localhost:8080/", 10, socketTimeoutSec);
  }

  public NakshaTestWebClient() {
    this(chooseSocketTimeout());
  }

  public HttpResponse<String> get(String subPath, String streamId, Duration timeout, @Nullable String jwt)
          throws URISyntaxException, IOException, InterruptedException {
    final RequestOptions request = request(HttpMethod.GET, subPath).addHeader(HDR_STREAM_ID, streamId);
    addJwt(request, jwt);
    return sendOnce(request, null, timeout);
  }

  public HttpResponse<String> get(String subPath, String streamId)
      throws URISyntaxException, IOException, InterruptedException {
    return this.get(subPath, streamId, SOCKET_TIMEOUT, null);
  }

  public HttpResponse<String> get(String subPath, String streamId, String jwt)
          throws URISyntaxException, IOException, InterruptedException {
    return this.get(subPath, streamId, SOCKET_TIMEOUT, jwt);
  }

  public HttpResponse<String> post(String subPath, String jsonBody, String streamId, Duration timeout, @Nullable String jwt)
          throws URISyntaxException, IOException, InterruptedException {
    final RequestOptions request = request(HttpMethod.POST, subPath)
        .addHeader("Content-Type", "application/json")
        .addHeader(HDR_STREAM_ID, streamId);
    addJwt(request, jwt);
    return sendOnce(request, jsonBody, timeout);
  }

  public HttpResponse<String> post(String subPath, String jsonBody, String streamId)
      throws URISyntaxException, IOException, InterruptedException {
    return this.post(subPath, jsonBody, streamId, SOCKET_TIMEOUT, null);
  }

  public HttpResponse<String> post(String subPath, String jsonBody, String streamId, String jwt)
          throws URISyntaxException, IOException, InterruptedException {
    return this.post(subPath, jsonBody, streamId, SOCKET_TIMEOUT, jwt);
  }

  public HttpResponse<String> post(String subPath, String jsonBody, String streamId, Duration timeout)
          throws URISyntaxException, IOException, InterruptedException {
    return this.post(subPath, jsonBody, streamId, timeout, null);
  }

  public HttpResponse<String> put(String subPath, String jsonBody, String streamId)
      throws URISyntaxException, IOException, InterruptedException {
    return this.put(subPath, jsonBody, streamId, null);
  }

  public HttpResponse<String> put(String subPath, String jsonBody, String streamId, @Nullable String jwt)
      throws URISyntaxException, IOException, InterruptedException {
    final RequestOptions request = request(HttpMethod.PUT, subPath)
        .addHeader("Content-Type", "application/json")
        .addHeader(HDR_STREAM_ID, streamId);
    addJwt(request, jwt);
    return sendOnce(request, jsonBody, SOCKET_TIMEOUT);
  }

  public HttpResponse<String> options(String subPath, String originHeader, String requestMethod, String reqHeaderKeys)
          throws URISyntaxException, IOException, InterruptedException {
    final RequestOptions request = request(HttpMethod.OPTIONS, subPath)
        .addHeader("Origin", originHeader)
        .addHeader("Access-Control-Request-Method", requestMethod)
        .addHeader("Access-Control-Request-Headers", reqHeaderKeys);
    return sendOnce(request, null, SOCKET_TIMEOUT);
  }

  public HttpResponse<String> patch(String subPath, String jsonBody, String streamId)
          throws URISyntaxException, IOException, InterruptedException {
    final RequestOptions request = request(HttpMethod.PATCH, subPath)
        .addHeader("Content-Type", "application/json")
        .addHeader(HDR_STREAM_ID, streamId);
    return sendOnce(request, jsonBody, SOCKET_TIMEOUT);
  }

  public HttpResponse<String> delete(String subPath, String streamId)
      throws URISyntaxException, IOException, InterruptedException {
    return this.delete(subPath, streamId, null);
  }

  public HttpResponse<String> delete(String subPath, String streamId, @Nullable String jwt)
      throws URISyntaxException, IOException, InterruptedException {
    final RequestOptions request = request(HttpMethod.DELETE, subPath)
        .addHeader("Content-Type", "application/json")
        .addHeader(HDR_STREAM_ID, streamId);
    addJwt(request, jwt);
    return sendOnce(request, null, SOCKET_TIMEOUT);
  }

  /**
   * Creates request options for the given sub-path. The path is passed to Vert.x as a raw string, so it is sent
   * without any encoding (e.g. {@code >=} stays {@code >=} and is not turned into {@code %3E=}).
   */
  private RequestOptions request(HttpMethod method, String subPath) {
    return new RequestOptions()
        .setMethod(method)
        .setHost(host)
        .setPort(port)
        .setURI(basePath + subPath);
  }

  private static void addJwt(RequestOptions request, @Nullable String jwt) {
    if (jwt != null) {
      request.addHeader(AUTHORIZATION, jwt);
    }
  }

  private HttpResponse<String> sendOnce(RequestOptions request, @Nullable String body, Duration timeout)
      throws IOException, InterruptedException {
    logger.info("Sending {} request to {}", request.getMethod(), request.getURI());
    request.setIdleTimeout(timeout.toMillis());
    try {
      return httpClient
          .request(request)
          .compose(req -> body == null ? req.send() : req.send(Buffer.buffer(body)))
          .compose(resp -> resp.body().map(respBody -> toHttpResponse(resp, respBody)))
          .toCompletionStage()
          .toCompletableFuture()
          // the idle timeout above should fire first, this is only a safety net
          .get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new HttpTimeoutException("Request timed out: " + request.getMethod() + " " + request.getURI());
    } catch (ExecutionException e) {
      throw new IOException("Request failed: " + request.getMethod() + " " + request.getURI(), e.getCause());
    }
  }

  private static HttpResponse<String> toHttpResponse(HttpClientResponse resp, Buffer body) {
    final Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : resp.headers().names()) {
      headers.put(name, resp.headers().getAll(name));
    }
    return new StringResponse(resp.statusCode(), HttpHeaders.of(headers, (name, value) -> true), body.toString());
  }

  private static synchronized Vertx sharedVertx() {
    if (vertx == null) {
      final Vertx created = Vertx.vertx();
      Runtime.getRuntime().addShutdownHook(new Thread(created::close, "naksha-test-web-client-shutdown"));
      vertx = created;
    }
    return vertx;
  }

  private static long chooseSocketTimeout(){
    if(ALLOW_DEBUG_SPECIFIC_TIMEOUT && DebugDiscoverUtil.isAppRunningOnDebug()){
      return DEBUG_SOCKET_TIMEOUT_SEC;
    }
    return DEFAULT_NON_DEBUG_SOCKET_TIMEOUT_SEC;
  }

  /**
   * Minimal {@link HttpResponse} holding what tests use: status code, headers and body. The request and URI are not
   * available, because the raw request path might not be a valid {@link URI}.
   */
  private record StringResponse(int statusCode, HttpHeaders headers, String body) implements HttpResponse<String> {

    @Override
    public HttpRequest request() {
      throw new UnsupportedOperationException("request() is not available in NakshaTestWebClient responses");
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      throw new UnsupportedOperationException("uri() is not available in NakshaTestWebClient responses");
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }
  }
}
