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
package com.here.naksha.lib.view;

import com.here.naksha.lib.core.lambdas.Fe1;
import com.here.naksha.lib.core.models.naksha.Storage;
import java.util.Map;
import java.util.concurrent.Future;
import naksha.base.Int64;
import naksha.base.PlatformMap;
import naksha.jbon.IDictManager;
import naksha.model.*;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class View implements IView {

  private Storage storage;

  private ViewLayerCollection viewLayerCollection;

  public View(@NotNull ViewLayerCollection viewLayerCollection) {
    this.viewLayerCollection = viewLayerCollection;
  }

  public View(Storage storage) {
    this.storage = storage;
  }

  @Override
  public ViewLayerCollection getViewCollection() {
    return viewLayerCollection;
  }

  @Override
  public @NotNull ViewReadSession newReadSession(@Nullable NakshaContext context, boolean useMaster) {
    return new ViewReadSession(this, context, useMaster);
  }

  @Override
  public @NotNull ViewWriteSession newWriteSession(@Nullable NakshaContext context, boolean useMaster) {
    return new ViewWriteSession(this, context, useMaster);
  }

//  @Override
//  public @NotNull <T> Future<T> shutdown(@Nullable Fe1<T, IStorage> onShutdown) {
//    throw new NotImplementedException();
//  }

//  @Override
//  public void initStorage() {
//    throw new UnsupportedOperationException("init all individual storages first");
//  }

//  @Override
//  public void startMaintainer() {
//    throw new NotImplementedException();
//  }
//
//  @Override
//  public void maintainNow() {
//    throw new NotImplementedException();
//  }
//
//  @Override
//  public void stopMaintainer() {
//    throw new NotImplementedException();
//  }

  @Override
  public void setViewLayerCollection(ViewLayerCollection viewLayerCollection) {
    this.viewLayerCollection = viewLayerCollection;
  }

  @NotNull
  @Override
  public String id() {
    return storage.getId();
  }

  @Override
  public void initStorage(@Nullable Map<String, ?> params) {
    storage.getOrInit();
  }

  @Override
  public void initRealm(@NotNull String realm) {}

  @Override
  public void dropRealm(@NotNull String realm) {}

  @NotNull
  @Override
  public NakshaFeatureProxy rowToFeature(@NotNull Row row) {
    return null;
  }

  @NotNull
  @Override
  public Row featureToRow(@NotNull PlatformMap feature) {
    return null;
  }

  @NotNull
  @Override
  public IDictManager dictManager(@NotNull NakshaContext nakshaContext) {
    return null;
  }

  @NotNull
  @Override
  public ILock enterLock(@NotNull String id, @NotNull Int64 waitMillis) {
    return null;
  }

  @NotNull
  @Override
  public IWriteSession newWriteSession(@NotNull NakshaContext context) {
    return null;
  }

  @Override
  public void close() {}
}
