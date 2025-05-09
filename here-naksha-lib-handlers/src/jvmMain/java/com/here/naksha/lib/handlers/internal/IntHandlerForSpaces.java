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

import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static com.here.naksha.lib.handlers.internal.IntValidationUtil.SUCCESSFUL_VALIDATION;
import static naksha.model.NakshaContext.currentContext;
import static naksha.model.util.RequestHelper.readFeaturesByIdsRequest;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.Space;
import java.util.List;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.util.ResultHelper;
import org.jetbrains.annotations.NotNull;

public class IntHandlerForSpaces extends AdminFeatureEventHandler<Space> {

  public IntHandlerForSpaces(final @NotNull INaksha hub) {
    super(hub, Space.class);
  }

  @Override
  protected @NotNull Response validateDeleteInstruction(Write write) {
    // DELETE does not require any validation
    return SUCCESSFUL_VALIDATION;
  }

  @Override
  protected @NotNull Response validateNonDeleteInstruction(Write write) {
    Response basicValidation = IntValidationUtil.basicValidationFor(write);
    if (basicValidation instanceof ErrorResponse) {
      return basicValidation;
    }
    Space space = (Space) write.getFeature();
    return handlerExistenceValidation(space);
  }

  private @NotNull Response handlerExistenceValidation(Space space) {
    List<String> missingHandlerIds = getMissingHandlersFor(space);
    if (missingHandlerIds.isEmpty()) {
      return SUCCESSFUL_VALIDATION;
    } else {
      return new ErrorResponse(
          NakshaError.NOT_FOUND,
          "Following handlers defined for Space %s don't exist: %s"
              .formatted(space.getId(), String.join(",", missingHandlerIds)));
    }
  }

  private List<String> getMissingHandlersFor(Space space) {
    List<String> expectedHandlerIds = space.getEventHandlerIds();
    ReadFeatures getEventHandlersRequest = readFeaturesByIdsRequest(nakshaHub.getAdminMapId(), EVENT_HANDLERS, expectedHandlerIds);
    return nakshaHub().getSpaceStorage().useReadSession(SessionOptions.from(currentContext()), readSession -> {
      Response result = readSession.execute(getEventHandlersRequest);
      return missingHandlersIds(result, expectedHandlerIds);
    });
  }

  private List<String> missingHandlersIds(Response fetchedHandlers, List<String> expectedHandlersIds) {
    List<String> availableHandlerIds = ResultHelper.readIdsFromResult(fetchedHandlers);
    return expectedHandlersIds.stream()
        .filter(expectedId -> !availableHandlerIds.contains(expectedId))
        .toList();
  }
}
