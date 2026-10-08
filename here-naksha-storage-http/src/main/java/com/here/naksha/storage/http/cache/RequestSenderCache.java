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
package com.here.naksha.storage.http.cache;

import com.here.naksha.storage.http.RequestSender;
import com.here.naksha.storage.http.RequestSender.KeyProperties;
import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestSenderCache {

  public static final int CLEANER_PERIOD_HOURS = 8;
  private final ConcurrentMap<String, RequestSenderWithLastUpdatedAt> requestSenders;

  /**
   * Represents a request sender instance with its configuration version timestamp.
   */
  record RequestSenderWithLastUpdatedAt(RequestSender instance, long storageUpdatedAt) {}

  private RequestSenderCache() {
    this(new ConcurrentHashMap<>(), CLEANER_PERIOD_HOURS, TimeUnit.HOURS);
  }

  RequestSenderCache(
      ConcurrentMap<String, RequestSenderWithLastUpdatedAt> requestSenders,
      int cleanPeriod,
      TimeUnit cleanPeriodUnit) {
    this.requestSenders = requestSenders;
    Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(requestSenders::clear, cleanPeriod, cleanPeriod, cleanPeriodUnit);
  }

  @NotNull
  public static RequestSenderCache getInstance() {
    return InstanceHolder.instance;
  }

  @NotNull
  public RequestSender getSenderWith(KeyProperties keyProperties) {
    return requestSenders
        .compute(keyProperties.name(), (__, cached) -> getUpdated(cached, keyProperties))
        .instance();
  }

  private @NotNull RequestSenderWithLastUpdatedAt getUpdated(
      @Nullable RequestSenderWithLastUpdatedAt cached, @NotNull KeyProperties keyProperties) {
    if (cached != null && cached.storageUpdatedAt() >= keyProperties.storageUpdatedAt()) {
      return cached;
    }
    RequestSender newInstance = new RequestSender(keyProperties);
    return new RequestSenderWithLastUpdatedAt(newInstance, keyProperties.storageUpdatedAt());
  }

  private static final class InstanceHolder {
    private static final RequestSenderCache instance = new RequestSenderCache();
  }
}
