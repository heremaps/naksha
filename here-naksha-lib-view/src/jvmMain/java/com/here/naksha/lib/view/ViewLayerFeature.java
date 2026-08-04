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
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper around an {@link NakshaFeature} returned by a specific {@link ViewLayer}.
 * @since 2.0
 */
public final class ViewLayerFeature {
  private final @NotNull NakshaFeature feature;
  // priority 0 - is highest
  private final int storagePriority;
  private final @NotNull ViewLayer viewLayer;

  /**
   * Wrap the given object.
   * @param feature the object to wrap.
   * @param storagePriority ?
   * @param viewLayer the layer from which the object originates.
   * @since 2.0
   */
  public ViewLayerFeature(@NotNull NakshaFeature feature, int storagePriority, @NotNull ViewLayer viewLayer) {
    this.feature = feature;
    this.storagePriority = storagePriority;
    this.viewLayer = viewLayer;
  }

  // TODO: @AI: What is the meaning of this? Add a documentation, it is missing as well in constructor.
  public int getStoragePriority() {
    return storagePriority;
  }

  /**
   * The layer from which the feature was returned.
   */
  public @NotNull ViewLayer getViewLayer() {
    return viewLayer;
  }

  /**
   * The feature.
   */
  public @NotNull NakshaFeature getFeature() {
    return feature;
  }
}
