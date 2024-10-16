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
package com.here.naksha.lib.view;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.storage.Notification;
import com.here.naksha.lib.core.models.storage.ReadRequest;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.storage.IReadSession;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class MockReadSession implements IReadSession {

  List<XyzFeatureCodec> results;

  public MockReadSession(List<XyzFeatureCodec> results) {
    this.results = results;
  }

  @Override
  public boolean isMasterConnect() {
    return false;
  }

  @Override
  public @NotNull NakshaContext getNakshaContext() {
    return null;
  }

  @Override
  public int getFetchSize() {
    return 0;
  }

  @Override
  public void setFetchSize(int size) {}

  @Override
  public long getStatementTimeout(@NotNull TimeUnit timeUnit) {
    return 0;
  }

  @Override
  public void setStatementTimeout(long timeout, @NotNull TimeUnit timeUnit) {}

  @Override
  public long getLockTimeout(@NotNull TimeUnit timeUnit) {
    return 0;
  }

  @Override
  public void setLockTimeout(long timeout, @NotNull TimeUnit timeUnit) {}

  @Override
  public @NotNull Result execute(@NotNull ReadRequest<?> readRequest) {
    return new MockResult<>(results);
  }

  @Override
  public @NotNull Result process(@NotNull Notification<?> notification) {
    return null;
  }

  @Override
  public void close() {}
}
