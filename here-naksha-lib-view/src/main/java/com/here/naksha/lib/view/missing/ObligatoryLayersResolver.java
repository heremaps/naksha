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
package com.here.naksha.lib.view.missing;

import com.here.naksha.lib.view.MissingIdResolver;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerFeature;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ObligatoryLayersResolver implements MissingIdResolver {

  private final Set<ViewLayer> obligatoryLayers;

  public ObligatoryLayersResolver(@NotNull Set<@NotNull ViewLayer> obligatoryLayers) {
    this.obligatoryLayers = obligatoryLayers;
  }

  @Override
  public boolean skip() {
    return false;
  }

  @Override
  public @Nullable List<Pair<ViewLayer, String>> layersToSearch(@NotNull List<ViewLayerFeature> multiFeature) {

    if (multiFeature.isEmpty()) {
      return null;
    }

    List<ViewLayer> layersHavingFeature =
        multiFeature.stream().map(ViewLayerFeature::getViewLayerRef).collect(Collectors.toList());

    List<Pair<ViewLayer, String>> missingObligatoryLayers = obligatoryLayers.stream()
        .filter(obligatoryLayer -> !layersHavingFeature.contains(obligatoryLayer))
        .map(layer -> Pair.of(layer, multiFeature.get(0).getTuple().id()))
        .collect(Collectors.toList());

    return missingObligatoryLayers;
  }
}
