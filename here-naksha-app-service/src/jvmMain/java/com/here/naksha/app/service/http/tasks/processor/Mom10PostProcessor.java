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

import static com.here.naksha.mom10.Mom10Verification.isMom10OrGreater;

import com.here.naksha.mom10.Mom10Transformation;
import naksha.model.objects.NakshaFeature;

/**
 * Post Processor that drops pre MOM 10 namespaces in the feature if it's version is 10 or above.
 */
public final class Mom10PostProcessor implements FeaturePostProcessor<NakshaFeature> {

  public static final Mom10PostProcessor MOM_10_POST_PROCESSOR = new Mom10PostProcessor();

  private Mom10PostProcessor() {
  }

  @Override
  public NakshaFeature postProcess(NakshaFeature feature) {
    if (isMom10OrGreater(feature)) {
      Mom10Transformation.dropPreMom10Namespaces(feature);
    }
    return feature;
  }
}
