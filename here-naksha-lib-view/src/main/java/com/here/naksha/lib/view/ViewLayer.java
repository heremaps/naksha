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

import naksha.model.IStorage;
import org.jetbrains.annotations.NotNull;

public class ViewLayer {

  private final @NotNull IStorage storage;
  private final @NotNull String mapId;
  private final @NotNull String collectionId;

  public ViewLayer(@NotNull IStorage storage, @NotNull String mapId, @NotNull String collectionId) {
    this.storage = storage;
    this.mapId = mapId;
    this.collectionId = collectionId;
  }

  public @NotNull IStorage getStorage() {
    return storage;
  }

  public @NotNull String getMapId() {
    return mapId;
  }

  public @NotNull String getCollectionId() {
    return collectionId;
  }
}
