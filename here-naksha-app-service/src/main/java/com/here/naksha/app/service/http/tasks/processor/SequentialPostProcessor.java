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

public final class SequentialPostProcessor<T extends XyzFeature> implements FeaturePostProcessor<T> {

  private final FeaturePostProcessor<T>[] postProcessors;

  private SequentialPostProcessor(FeaturePostProcessor<T>[] postProcessors) {
    this.postProcessors = postProcessors;
  }

  public static <T extends XyzFeature> SequentialPostProcessor<T> combine(FeaturePostProcessor<T>... postProcessors) {
    return new SequentialPostProcessor<>(postProcessors);
  }

  @Override
  public T postProcess(T feature) {
    for (FeaturePostProcessor<T> postProcessor : postProcessors) {
      if (postProcessor != null) {
        feature = postProcessor.postProcess(feature);
      }
    }
    return feature;
  }
}
