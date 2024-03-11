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

import static com.here.naksha.storage.http.RequestSender.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestSenderCache {

  private static final Map<String, RequestSender> requestSenders = new HashMap<>();

  private static final ScheduledFuture<?> cleaner =
      Executors
              .newScheduledThreadPool(1)
              .scheduleAtFixedRate(requestSenders::clear, 0, 8, TimeUnit.HOURS);

  static RequestSender getSenderWith(KeyProperties keyProperties) {
    return requestSenders.compute(
        keyProperties.name(), (__, cachedSender) -> getSenderWith(cachedSender, keyProperties));
  }

  private static @NotNull RequestSender getSenderWith(
      @Nullable RequestSender cachedSender, @NotNull KeyProperties keyProperties) {
    if (cachedSender == null || !cachedSender.propertiesEquals(keyProperties))
      return new RequestSender(keyProperties);
    else return cachedSender;
  }
}
