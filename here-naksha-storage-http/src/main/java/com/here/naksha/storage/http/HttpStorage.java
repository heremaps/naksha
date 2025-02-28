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

import com.here.naksha.storage.http.RequestSender.KeyProperties;
import com.here.naksha.storage.http.cache.RequestSenderCache;
import naksha.base.Int64;
import naksha.base.JvmBoxingUtil;
import naksha.base.PlatformLock;
import naksha.jbon.JbDictionary;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HttpStorage implements IStorage {

  private final KeyProperties defaultKeyProperties;

  private final NakshaStorage storageConfig;

  public HttpStorage(@NotNull NakshaStorage storageConfig) {
    this.storageConfig = storageConfig;
    HttpStorageProperties properties = HttpStorage.getProperties(storageConfig);
    if (properties == null) {
        throw new IllegalArgumentException("A HTTP storage must have properties containing a 'url'");
    }
    defaultKeyProperties = new KeyProperties(
        storageConfig.getId(),
        properties.getUrl(),
        properties.getHeaders(),
        properties.getConnectTimeout(),
        properties.getSocketTimeout());
  }

  private static @Nullable HttpStorageProperties getProperties(@NotNull NakshaStorage storage) {
    return JvmBoxingUtil.box(storage.getProperties(), HttpStorageProperties.class);
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

  @NotNull
  @Override
  public String getId() {
    return defaultKeyProperties.name();
  }

  @Override
  public int getHardCap() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @NotNull PlatformLock getLock() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @NotNull NakshaStorage getConfig() {
    return storageConfig;
  }

  @Override
  public @NotNull Int64 getNumber() {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public int getEncodingFlags(@Nullable Object feature, @Nullable Object context) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable JbDictionary getDictionary(@NotNull String id) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }

  @Override
  public @Nullable JbDictionary getEncodingDictionary(@Nullable Object feature, @Nullable Object context) {
    throw new NotImplementedException("Not supported by HTTP storage");
  }
}
