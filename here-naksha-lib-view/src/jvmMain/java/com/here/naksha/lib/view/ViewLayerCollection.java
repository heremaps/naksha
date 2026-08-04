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

import naksha.base.TupleNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class ViewLayerCollection {

  private final @NotNull String name;
  private final @NotNull List<@NotNull ViewLayer> layers;

  public ViewLayerCollection(@NotNull String name, @NotNull List<@NotNull ViewLayer> layers) {
    this.name = name;
    this.layers = Collections.unmodifiableList(layers);
  }

  public ViewLayerCollection(@NotNull String name, @NotNull ViewLayer... orderedLowerLevelStorages) {
    this.name = name;
    this.layers = List.of(orderedLowerLevelStorages);
  }

  public @NotNull String getName() {
    return name;
  }

  public @NotNull List<@NotNull ViewLayer> getLayers() {
    return layers;
  }

  public int priorityOf(@NotNull ViewLayer layer) {
    return layers.indexOf(layer);
  }

  public @NotNull ViewLayer getTopPriorityLayer() {
    return layers.get(0);
  }

  public @Nullable ViewLayer getByTupleNumber(@NotNull TupleNumber tupleNumber) {
    for (@NotNull ViewLayer layer : layers) {
      if (layer.contains(tupleNumber)) return layer;
    }
    return null;
  }
}
