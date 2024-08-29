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

import static com.here.naksha.storage.http.RequestSender.KeyProperties;

import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.storage.http.cache.RequestSenderCache;

import java.util.Map;

import naksha.base.Int64;
import naksha.base.JvmProxyUtil;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpStorage implements IStorage {

  private static final Logger log = LoggerFactory.getLogger(HttpStorage.class);

  private final RequestSender requestSender;

  public HttpStorage(@NotNull Storage storage) {
    HttpStorageProperties properties = HttpStorage.getProperties(storage);
    requestSender = RequestSenderCache.getInstance()
        .getSenderWith(new KeyProperties(
            storage.getId(),
            properties.getUrl(),
            properties.getHeaders(),
            properties.getConnectTimeout(),
            properties.getSocketTimeout()));
  }

  public @NotNull IReadSession newReadSession(@Nullable NakshaContext context, boolean useMaster) {
    return new HttpStorageReadSession(context, useMaster, requestSender);
  }

  private static @NotNull HttpStorageProperties getProperties(@NotNull Storage storage) {
    return JvmProxyUtil.box(storage.getProperties(), HttpStorageProperties.class);
  }

  @Override
  public void close() {

  }

  @NotNull
  @Override
  public IReadSession newReadSession(@Nullable SessionOptions options) {
    return null;
  }

  @NotNull
  @Override
  public IWriteSession newWriteSession(@Nullable SessionOptions options) {
    return null;
  }

  @NotNull
  @Override
  public ILock enterLock(@NotNull String id, @NotNull Int64 waitMillis) {
    return IStorage.super.enterLock(id, waitMillis);
  }
  @Nullable
  @Override
  public String getMapId(int mapNumber) {
    return "";
  }

  @Override
  public boolean contains(@NotNull String mapId) {
    return false;
  }

  @NotNull
  @Override
  public IMap get(@NotNull String mapId) {
    return null;
  }

  @NotNull
  @Override
  public IMap getDefaultMap() {
    return null;
  }

  @Override
  public void initStorage(@Nullable Map<String, ?> params) {
    log.debug("HttpStorage.initStorage called");
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @NotNull
  @Override
  public SessionOptions getAdminOptions() {
    return null;
  }

  @NotNull
  @Override
  public String getId() {
    return "";
  }

  @NotNull
  @Override
  public NakshaFeature tupleToFeature(@NotNull Tuple tuple) {
    return null;
  }

  @NotNull
  @Override
  public Tuple featureToTuple(@NotNull NakshaFeature feature) {
    return null;
  }
}
