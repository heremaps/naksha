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

import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ViewLayerFeature {

  private final @NotNull FeatureTuple featureTuple;
  // priority 0 - is highest
  private final int storagePriority;
  private final @NotNull ViewLayer viewLayer;

  public ViewLayerFeature(@NotNull FeatureTuple featureTuple, int storagePriority, @NotNull ViewLayer viewLayer) {
    this.featureTuple = featureTuple;
    this.storagePriority = storagePriority;
    this.viewLayer = viewLayer;
  }

  public int getStoragePriority() {
    return storagePriority;
  }

  public @NotNull ViewLayer getViewLayer() {
    return viewLayer;
  }

  public @Nullable NakshaFeature getFeature() {
    return featureTuple.getFeature();
  }

  public @NotNull FeatureTuple getFeatureTuple() {
    return featureTuple;
  }
}
