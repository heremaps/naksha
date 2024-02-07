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

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.storage.Notification;
import com.here.naksha.lib.core.models.storage.ReadRequest;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.storage.IReadSession;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpStorageReadSession implements IReadSession {

  private static final Logger log = LoggerFactory.getLogger(HttpStorageReadSession.class);

  @NotNull
  final NakshaContext context;

  HttpStorageReadSession(@Nullable NakshaContext context) {
    this.context = context == null ? NakshaContext.currentContext() : context;
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
    log.info("Hello Naksha!");
    return new SuccessResult();
  }

  @Override
  public @NotNull Result process(@NotNull Notification<?> notification) {
    throw new NotImplementedException();
  }

  @Override
  public void close() {
    log.info("Bye Naksha!");
  }
}
