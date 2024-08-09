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

import com.here.naksha.lib.core.models.naksha.Storage;
import java.util.Map;
import naksha.base.Int64;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
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
  public @NotNull ViewReadSession newReadSession(@Nullable SessionOptions options) {
    return new ViewReadSession(this, options);
  }

  @Override
  public @NotNull ViewWriteSession newWriteSession(@Nullable SessionOptions options) {
    return new ViewWriteSession(this, options);
  }

  @Override
  public void setViewLayerCollection(ViewLayerCollection viewLayerCollection) {
    this.viewLayerCollection = viewLayerCollection;
  }

  @Override
  public void initStorage(@Nullable Map<String, ?> params) {
    //    storage.init();
  }

  @NotNull
  @Override
  public ILock enterLock(@NotNull String id, @NotNull Int64 waitMillis) {
    return null;
  }

  @Override
  public void close() {}

  @NotNull
  @Override
  public Tuple featureToRow(@NotNull NakshaFeature feature) {
    return null;
  }

  @NotNull
  @Override
  public NakshaFeature rowToFeature(@NotNull Tuple tuple) {
    return null;
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
}
