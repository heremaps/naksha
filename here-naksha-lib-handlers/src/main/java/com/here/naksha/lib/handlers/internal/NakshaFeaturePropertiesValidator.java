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
package com.here.naksha.lib.handlers.internal;

import naksha.model.NakshaError;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

class NakshaFeaturePropertiesValidator {

  private NakshaFeaturePropertiesValidator() {}

  static Response nakshaFeatureValidation(NakshaFeature feature) {
    Response titleValidation = requiredPropertyValidationError(feature.getTitle(), NakshaFeature.TITLE_KEY);
    if (titleValidation instanceof ErrorResponse) {
      return titleValidation;
    }
    Response descValidation =
            requiredPropertyValidationError(feature.getDescription(), NakshaFeature.DESCRIPTION_KEY);
    if (descValidation instanceof ErrorResponse) {
      return descValidation;
    }
    return new SuccessResponse();
  }

  private static @NotNull Response requiredPropertyValidationError(String value, String propertyName) {
    if (StringUtils.isBlank(value)) {
      return missingParameterError(propertyName);
    }
    return new SuccessResponse();
  }

  private static ErrorResponse missingParameterError(String propertyName) {
    return new ErrorResponse(
            NakshaError.ILLEGAL_ARGUMENT, "Mandatory parameter '" + propertyName + "' missing!", null, null);
  }
}
