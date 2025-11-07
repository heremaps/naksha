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

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.mom10.Mom10Verification;
import com.here.naksha.mom10.handler.TransformationSuccess.FromMom10;
import com.here.naksha.mom10.Mom10Transformation;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Handler responsible for populating outdated (pre MOM 10) namespaces: `@ns:com:here:mom:meta` and '@ns:com:here:mom:meta`.
 * The feature modification happens in-place, no other properties are modified.
 * This handler works only for features of MOM version 10 and above. It is intended to be part of the writing pipeline.
 * Reading of features process with this handler should be done with use of {@link ToMom10TransformationHandler}
 */
public final class FromMom10TransformationHandler extends AbstractEventHandler {

  public FromMom10TransformationHandler(@NotNull INaksha hub) {
    super(hub);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    if (event.getRequest() instanceof TransformFromMom10Request) {
      return PROCESS;
    }
    return NOT_IMPLEMENTED;
  }

  @Override
  protected @NotNull Result process(@NotNull IEvent event) {
    TransformFromMom10Request request = (TransformFromMom10Request) event.getRequest();
    List<XyzFeature> features = featuresOrEmptyList(request);
    try {
      features.forEach(this::validateVersionAndPopulatePreMom10Namespaces);
    } catch (IllegalArgumentException iae) {
      return new ErrorResult(XyzError.ILLEGAL_ARGUMENT, "MOM 10 transformation failed", iae);
    }
    return new FromMom10(features);
  }

  private List<XyzFeature> featuresOrEmptyList(TransformFromMom10Request request) {
    List<XyzFeature> features = request.getFeaturesToTransform();
    if (features == null) {
      return Collections.emptyList();
    }
    return features;
  }

  private void validateVersionAndPopulatePreMom10Namespaces(@NotNull XyzFeature feature) {
    if (Mom10Verification.isMom10OrGreater(feature)) {
      Mom10Transformation.populatePreMom10Namespaces(feature);
    } else {
      throw new IllegalArgumentException("Feature '" + feature.getId() + "' has version < 10.0.0");
    }
  }
}
