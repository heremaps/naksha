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

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.handlers.DefaultStorageHandlerProperties;
import com.here.naksha.storage.http.HttpStorage;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static com.here.naksha.lib.handlers.internal.HttpStorageValidation.validateConfigForHttpStorage;
import static com.here.naksha.lib.handlers.internal.IntValidationUtil.SUCCESSFUL_VALIDATION;
import static naksha.base.NakshaError.CONFLICT;
import static naksha.base.NakshaError.EXCEPTION;
import static naksha.model.util.ResultHelper.extractResponseItems;

public class IntHandlerForStorageConfigs extends AdminFeatureEventHandler<NakshaStorage> {

  public IntHandlerForStorageConfigs(final @NotNull INaksha hub) {
    super(hub, NakshaStorage.class);
  }

  @Override
  protected @NotNull Response validateDeleteInstruction(Write write) {
    // For DELETE, only the feature ID is needed, other JSON properties are irrelevant
    return noActiveHandlerValidation(write);
  }

  @Override
  protected @NotNull Response validateNonDeleteInstruction(Write write) {
    Response basicValidation = IntValidationUtil.basicValidationFor(write);
    if (basicValidation instanceof ErrorResponse) {
      return basicValidation;
    }
    final NakshaStorage storageConfig = write.getFeature(NakshaStorage.TYPE);
    if (storageConfig == null) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          "Storage Config can't be null"
      );
    }
    final String className = storageConfig.getClassName();
    final Response classNameValidation = validateClassName(className);
    if (classNameValidation instanceof ErrorResponse) {
      return classNameValidation;
    }
    if (HttpStorage.class.getName().equals(className)) {
      return validateConfigForHttpStorage(storageConfig);
    }

    return SUCCESSFUL_VALIDATION;
  }

  private Response validateClassName(String className) {
    if (className == null || className.isEmpty()) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          "Storage Config is missing mandatory parameter: '" + NakshaStorage.CLASSNAME_FIELD + "'"
      );
    }
    return SUCCESSFUL_VALIDATION;
  }

  private Response noActiveHandlerValidation(Write codec) {
    // Search for active event handlers still using this storage
    String storageId = codec.getId();
    if (storageId == null) {
      if (codec.getFeature() == null) {
        return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT, "No storage ID supplied.");
      }
      storageId = codec.getFeature().getId();
    }
    // Scan through all handlers with JSON property "properties.storageId" = <storage-id-to-be-deleted>
    final Property property =
        new Property(NakshaFeature.PROPERTIES_KEY, DefaultStorageHandlerProperties.STORAGE_ID);
    final PQuery activeHandlersPOp = new PQuery(property, StringOp.EQUALS, storageId);
    final ReadFeatures readActiveHandlersRequest = new ReadFeatures().addCollectionId(EVENT_HANDLERS)
            .withMapId(nakshaHub.getAdminMapId())
            .withPropertyQuery(activeHandlersPOp);
    Response activeHandlersResponse = nakshaHub().getAdminStorage()
        .useReadSession(SessionOptions.from(NakshaContext.currentContext()), readSession -> readSession.execute(readActiveHandlersRequest));
    if(activeHandlersResponse instanceof SuccessResponse successResponse) {
      final List<EventHandlerConfig> eventHandlers = extractResponseItems(successResponse, EventHandlerConfig.class);
      if(eventHandlers.isEmpty()) {
        return SUCCESSFUL_VALIDATION;
      }
      final List<String> handlerIds = eventHandlers.stream().map(NakshaFeature::getId).toList();
      return new ErrorResponse(CONFLICT, "The storage is still in use by these event handlers: " + handlerIds);
    } else if (activeHandlersResponse instanceof ErrorResponse errorResponse) {
        return errorResponse;
    } else {
      return new ErrorResponse(EXCEPTION, "Unexpected response while fetching storage's handlers: " + activeHandlersResponse);
    }
  }
}
