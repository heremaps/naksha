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

import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.storage.http.RequestSender.KeyProperties;
import com.here.naksha.storage.http.cache.RequestSenderCache;
import naksha.base.Int64;
import naksha.base.JvmProxyUtil;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpStorage implements IStorage {

  private static final Logger log = LoggerFactory.getLogger(HttpStorage.class);

  private final KeyProperties defaultKeyProperties;

  private final AtomicBoolean initialized = new AtomicBoolean(false);

  public HttpStorage(@NotNull Storage storage) {
    HttpStorageProperties properties = HttpStorage.getProperties(storage);
    if (properties == null) {
      if (!storage.getProperties().hasRaw(HttpStorageProperties.URL)) {
        throw new IllegalArgumentException("A HTTP storage must have properties containing a 'url'");
      }
      properties = new HttpStorageProperties(
          storage.getProperties().get(HttpStorageProperties.URL).toString(), null, null, null);
    }
    defaultKeyProperties = new KeyProperties(
            storage.getId(),
            properties.getUrl(),
            properties.getHeaders(),
            properties.getConnectTimeout(),
            properties.getSocketTimeout());
  }

  private static @Nullable HttpStorageProperties getProperties(@NotNull Storage storage) {
    return JvmProxyUtil.box(storage.getProperties(), HttpStorageProperties.class);
  }

  @Override
  public void close() {
  }

  @NotNull
  @Override
  public IReadSession newReadSession(@Nullable SessionOptions options) {
    final RequestSender requestSender = RequestSenderCache.getInstance()
            .getSenderWith(new KeyProperties(
                    defaultKeyProperties.name(),
                    defaultKeyProperties.hostUrl(),
                    defaultKeyProperties.defaultHeaders(),
                    options != null ? options.connectTimeout : defaultKeyProperties.connectionTimeoutSec(),
                    options != null ? options.socketTimeout : defaultKeyProperties.socketTimeoutSec()));
    return new HttpStorageReadSession(NakshaContext.currentContext(), requestSender);
  }

  @NotNull
  @Override
  public IWriteSession newWriteSession(@Nullable SessionOptions options) {
    throw new NotImplementedException("Not yet supported");
  }

  @Nullable
  @Override
  public String getMapId(int mapNumber) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public boolean contains(@NotNull String mapId) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public IMap get(@NotNull String mapId) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public IMap getDefaultMap() {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public void initStorage(@Nullable Map<String, ?> params) {
    log.debug("HttpStorage.initStorage called");
    initialized.set(true);
    // TODO processing params when needed
  }

  @Override
  public boolean isInitialized() {
    return initialized.get();
  }

  @NotNull
  @Override
  public SessionOptions getAdminOptions() {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public String getId() {
    return defaultKeyProperties.name();
  }

  @NotNull
  @Override
  public NakshaFeature tupleToFeature(@NotNull Tuple tuple) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public Tuple featureToTuple(@NotNull NakshaFeature feature) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @NotNull
  @Override
  public ILock enterLock(@NotNull String id, @NotNull Int64 waitMillis) {
    throw new NotImplementedException("Enter lock not supported");
  }

  @Override
  public int getHardCap() {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Override
  public void setHardCap(int i) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }

  @Nullable
  @Override
  public IMap get(int mapNumber) {
    throw new NotImplementedException("Not supported for HTTP storage");
  }
}
