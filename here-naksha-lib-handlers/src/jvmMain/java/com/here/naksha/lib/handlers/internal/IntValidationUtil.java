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

import static naksha.model.NakshaContext.currentContext;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.util.CustomIndexMappingCompiler;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.request.ReadCollections;
import naksha.model.util.CustomStoragePropertiesUtil;
import naksha.model.util.ResultHelper;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class IntValidationUtil {

  static final SuccessResponse SUCCESSFUL_VALIDATION = new SuccessResponse();

  private IntValidationUtil() {
  }

  static Response basicValidationFor(Write write) {
    NakshaFeature feature = write.getFeature();
    if (feature == null) {
      return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT, "Feature of write operation can't be null");
    }
    return basicValidationFor(feature);
  }

  static Response basicValidationFor(NakshaFeature feature) {
    Response titleValidation = requiredPropertyValidationError(feature.getTitle(), NakshaFeature.TITLE_KEY);
    if (titleValidation instanceof ErrorResponse) {
      return titleValidation;
    }
    Response descValidation =
        requiredPropertyValidationError(feature.getDescription(), NakshaFeature.DESCRIPTION_KEY);
    if (descValidation instanceof ErrorResponse) {
      return descValidation;
    }
    return SUCCESSFUL_VALIDATION;
  }

  /**
   * Validates the custom index mappings of a Space or Handler collection definition. If the collection exists already in
   * the given storage, it must match the mappings. A missing collection is accepted, it is created on the first write.
   */
  static @NotNull Response collectionMappingValidation(
      @NotNull INaksha hub, @Nullable String storageId, @Nullable NakshaCollection collection) {
    try {
      if (storageId == null || !CustomIndexMappingCompiler.hasMappings(collection)) {
        return SUCCESSFUL_VALIDATION;
      }
      CustomIndexMappingCompiler.validate(collection);
      final IStorage storage = hub.getStorageById(storageId);
      final ReadCollections readCollection = new ReadCollections()
          .withMapId(CustomStoragePropertiesUtil.getSchema(storage.getConfig()))
          .addCollectionId(collection.getId());
      final Response response = storage.useReadSession(
          SessionOptions.from(currentContext()), readSession -> readSession.executeRead(readCollection));
      if (response instanceof ErrorResponse) {
        final String code = ((ErrorResponse) response).getError().getCode();
        return NakshaError.COLLECTION_NOT_FOUND.equals(code) || NakshaError.MAP_NOT_FOUND.equals(code)
            ? SUCCESSFUL_VALIDATION
            : response;
      }
      final NakshaCollection stored =
          ResultHelper.readFeatureFromResponse((SuccessResponse) response, NakshaCollection.class);
      if (stored != null) {
        CustomIndexMappingCompiler.requireMatching(stored, collection);
      }
      return SUCCESSFUL_VALIDATION;
    } catch (NakshaException e) {
      return new ErrorResponse(e.getError());
    }
  }

  private static @NotNull Response requiredPropertyValidationError(String value, String propertyName) {
    if (StringUtils.isBlank(value)) {
      return missingParameterError(propertyName);
    }
    return SUCCESSFUL_VALIDATION;
  }

  private static ErrorResponse missingParameterError(String propertyName) {
    return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT, "Mandatory parameter '" + propertyName + "' missing!");
  }
}
