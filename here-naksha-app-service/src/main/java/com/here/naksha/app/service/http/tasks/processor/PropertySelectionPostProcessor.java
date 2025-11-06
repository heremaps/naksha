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
package com.here.naksha.app.service.http.tasks.processor;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.util.PropertyPathUtil;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.util.Map;
import java.util.Set;

public final class PropertySelectionPostProcessor implements FeaturePostProcessor<XyzFeature> {

  private final Set<String> propPaths;

  public PropertySelectionPostProcessor(Set<String> propPaths) {
    this.propPaths = propPaths;
  }

  @Override
  public XyzFeature postProcess(XyzFeature feature) {
    final Map<String, Object> tgtMap = PropertyPathUtil.extractPropertyMapFromFeature(feature, propPaths);
    return JsonSerializable.fromMap(tgtMap, XyzFeature.class);
  }
  /**
   *   @SuppressWarnings("unchecked")
   *   private <F extends XyzFeature> @NotNull F applyPropertySelection(
   *       final @NotNull F f, final @NotNull Set<String> propPaths) {
   *     final Map<String, Object> tgtMap = PropertyPathUtil.extractPropertyMapFromFeature(f, propPaths);
   *     return (F) JsonSerializable.fromMap(tgtMap, f.getClass());
   *   }
   */
}
