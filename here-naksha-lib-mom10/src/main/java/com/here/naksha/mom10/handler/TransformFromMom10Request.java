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
package com.here.naksha.mom10.handler;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.Request;
import java.util.List;

public class TransformFromMom10Request extends Request<TransformFromMom10Request> {

  private final List<XyzFeature> featuresToTransform;

  public TransformFromMom10Request(List<XyzFeature> preMom10Features) {
    this.featuresToTransform = preMom10Features;
  }

  /**
   * Returns features that should be transformed from MOM 10 into pre-MOM 10 format with use of {@link FromMom10TransformationHandler}
   * Important: as his transformation is applied on the features themselves, calling this getter after transformation, will return transformed features.
   * @return features to be transformed from MOM 10 (or already transformed if transformation was called)
   */
  public List<XyzFeature> getFeaturesToTransform() {
    return featuresToTransform;
  }
}
