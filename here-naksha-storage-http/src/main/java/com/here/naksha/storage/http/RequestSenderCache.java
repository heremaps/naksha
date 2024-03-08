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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestSenderCache {

  private static final Map<String, RequestSender> requestSenders = new HashMap<>();

  private static final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1);

  static {
    cleaner.scheduleAtFixedRate(requestSenders::clear, 0, 8, TimeUnit.HOURS);
  }

  static RequestSender get(
      String id, String url, Map<String, String> headers, Long connectTimeout, Long socketTimeout) {
    return requestSenders.compute(
        id, (__, cachedSender) -> get(cachedSender, id, url, headers, connectTimeout, socketTimeout));
  }

  private static @NotNull RequestSender get(
      @Nullable RequestSender cachedSender,
      String id,
      String url,
      Map<String, String> headers,
      Long connectTimeout,
      Long socketTimeout) {
    if (cachedSender == null || !cachedSender.propertiesEquals(id, url, headers, connectTimeout, socketTimeout))
      return new RequestSender(id, url, headers, connectTimeout, socketTimeout);
    else return cachedSender;
  }
}
