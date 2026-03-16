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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static java.net.http.HttpRequest.newBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestSender {

    private static final Logger log = LoggerFactory.getLogger(RequestSender.class);

    @NotNull
    private HttpClient httpClient;

    @NotNull
    final RequestSender.KeyProperties keyProps;

    public RequestSender(@NotNull RequestSender.KeyProperties keyProps) {
        this.keyProps = keyProps;
        this.httpClient = createNewClient();
    }

    private HttpClient createNewClient() {
        return HttpClientFactory.getHttpClient(Duration.ofSeconds(keyProps.connectionTimeoutSec));
    }

    /**
     * Send a request configured based on enclosing {@link HttpStorage}.
     *
     * @param endpoint   does not contain host:port part, starts with "/".
     * @param addHeaders headers to be added to the ones defines {@link KeyProperties#defaultHeaders}.
     */
    public HttpResponse<byte[]> sendRequest(@NotNull String endpoint, @Nullable Map<String, String> addHeaders) {
        return sendRequest(endpoint, true, addHeaders, null, null);
    }

    public HttpResponse<byte[]> post(String endpoint, String body) {
        return sendRequest(endpoint, true, null, "POST", body);
    }

    HttpResponse<byte[]> sendRequest(
            @NotNull String endpoint,
            boolean keepDefHeaders,
            @Nullable Map<String, String> headers,
            @Nullable String httpMethod,
            @Nullable String body) {
        URI uri = URI.create(keyProps.hostUrl + endpoint);
        HttpRequest.Builder builder = newBuilder().uri(uri).timeout(Duration.ofSeconds(keyProps.socketTimeoutSec));

        if (keepDefHeaders) keyProps.defaultHeaders.forEach(builder::header);
        if (headers != null) headers.forEach(builder::header);

        HttpRequest.BodyPublisher bodyPublisher =
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        if (httpMethod != null) builder.method(httpMethod, bodyPublisher);
        HttpRequest request = builder.build();

        HttpResponse<byte[]> response = null;
        for (int i = 0; i <= keyProps.maxRetries; i++) {
            long startTime = System.currentTimeMillis();
            try {
                CompletableFuture<HttpResponse<byte[]>> futureResponse =
                        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
                response = futureResponse.get(keyProps.socketTimeoutSec, TimeUnit.SECONDS);
                break;
            } catch (Exception e) {
                if (isRetryEligibleException(e) && i < keyProps.maxRetries) {
                    log.warn(
                            "We got retryable exception while executing Http request against remote server. Current retry attempt {} of {}. ",
                            i,
                            keyProps.maxRetries,
                            e);
                    // reset HttpClient and then retry request
                    this.httpClient = createNewClient();
                } else {
                    log.warn("We got exception while executing Http request against remote server. ", e);
                    throw unchecked(e);
                }
            } finally {
                long executionTime = System.currentTimeMillis() - startTime;
                log.info(
                        "[Storage API stats => type,storageId,host,method,path,status,timeTakenMs,resSize] - StorageAPIStats {} {} {} {} {} {} {} {}",
                        "HttpStorage",
                        keyProps.name,
                        keyProps.hostUrl,
                        request.method(),
                        request.uri(),
                        (response == null) ? "-" : response.statusCode(),
                        executionTime,
                        (response == null) ? 0 : response.body().length);
            }
        }
        return response;
    }

    private boolean isRetryEligibleException(@NotNull final Exception e) {
        if (!(e instanceof ExecutionException)) {
            return false;
        }
        Throwable cause = e.getCause();
        if (!(cause instanceof IOException)) {
            return false;
        }
        IOException ioe = (IOException) cause;
        return ioe.getMessage() != null && ioe.getMessage().contains("GOAWAY");
    }

    public boolean hasKeyProps(KeyProperties thatKeyProps) {
        return this.keyProps.equals(thatKeyProps);
    }

    public static final class KeyProperties {
        private final @NotNull String name;
        private final @NotNull String hostUrl;
        private final @NotNull Map<String, String> defaultHeaders;
        private final int connectionTimeoutSec;
        private final int socketTimeoutSec;
        private final int maxRetries;

        public KeyProperties(
                @NotNull String name,
                @NotNull String hostUrl,
                @NotNull Map<String, String> defaultHeaders,
                int connectionTimeoutSec,
                int socketTimeoutSec,
                int maxRetries) {
            this.name = name;
            this.hostUrl = hostUrl;
            this.defaultHeaders = defaultHeaders;
            this.connectionTimeoutSec = connectionTimeoutSec;
            this.socketTimeoutSec = socketTimeoutSec;
            this.maxRetries = maxRetries;
        }

        public @NotNull String getName() {
            return name;
        }

        public @NotNull String getHostUrl() {
            return hostUrl;
        }

        public @NotNull Map<String, String> getDefaultHeaders() {
            return defaultHeaders;
        }

        public int getConnectionTimeoutSec() {
            return connectionTimeoutSec;
        }

        public int getSocketTimeoutSec() {
            return socketTimeoutSec;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof KeyProperties)) {
                return false;
            }
            KeyProperties that = (KeyProperties) o;
            return connectionTimeoutSec == that.connectionTimeoutSec
                    && socketTimeoutSec == that.socketTimeoutSec
                    && maxRetries == that.maxRetries
                    && name.equals(that.name)
                    && hostUrl.equals(that.hostUrl)
                    && defaultHeaders.equals(that.defaultHeaders);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, hostUrl, defaultHeaders, connectionTimeoutSec, socketTimeoutSec, maxRetries);
        }

        @Override
        public String toString() {
            return "KeyProperties["
                    + "name=" + name
                    + ", hostUrl=" + hostUrl
                    + ", defaultHeaders=" + defaultHeaders
                    + ", connectionTimeoutSec=" + connectionTimeoutSec
                    + ", socketTimeoutSec=" + socketTimeoutSec
                    + ", maxRetries=" + maxRetries
                    + ']';
        }
    }
}
