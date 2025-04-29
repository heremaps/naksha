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
package com.here.naksha.lib.view.concurrent;

import com.here.naksha.lib.view.ViewLayer;
import naksha.base.StringList;
import naksha.model.IReadSession;
import naksha.model.request.ReadFeatures;
import org.jetbrains.annotations.NotNull;

public class LayerReadRequest {

  private final @NotNull ReadFeatures request;
  private final @NotNull ViewLayer viewLayer;
  private final @NotNull IReadSession session;

  public LayerReadRequest(@NotNull ReadFeatures request, @NotNull ViewLayer viewLayer, @NotNull IReadSession session) {
    // Note: We need to copy the request, because we need to ignore the map/collection client asked for,
    //       because the view is always fixed to certain map/collection!
    this.request = request.copy(false);
    this.request.setMapId(viewLayer.getMapId());
    this.request.setCollectionIds(new StringList(viewLayer.getCollectionId()));
    this.viewLayer = viewLayer;
    this.session = session;
  }

  public @NotNull ReadFeatures getRequest() {
    return request;
  }

  public @NotNull ViewLayer getViewLayer() {
    return viewLayer;
  }

  public @NotNull IReadSession getSession() {
    return session;
  }
}
