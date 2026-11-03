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

import com.here.naksha.lib.core.util.PropertyPathUtil;
import java.util.Map;
import java.util.Set;
import naksha.base.Platform;
import naksha.model.objects.NakshaFeature;

public final class PropertySelectionPostProcessor<T extends NakshaFeature> implements FeaturePostProcessor<T> {

  private final Set<String> propPaths;

  public PropertySelectionPostProcessor(Set<String> propPaths) {
    this.propPaths = propPaths;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T postProcess(T feature) {
    final Map<String, Object> targetMap = PropertyPathUtil.extractPropertyMapFromFeature(feature, propPaths);
    final Class<T> type = (Class<T>) feature.getClass();
    return Platform.javaProxy(Platform.fromJSON(Platform.toJSON(targetMap)), type);
  }
}
