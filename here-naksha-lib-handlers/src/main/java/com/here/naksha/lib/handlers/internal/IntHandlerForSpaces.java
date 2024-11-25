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
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import naksha.model.IReadSession;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.here.naksha.lib.core.NakshaAdminCollection.EVENT_HANDLERS;
import static com.here.naksha.lib.core.util.storage.RequestHelper.readFeaturesByIdsRequest;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readIdsFromResult;
import static naksha.model.NakshaContext.currentContext;

public class IntHandlerForSpaces extends AdminFeatureEventHandler<Space> {

  public IntHandlerForSpaces(final @NotNull INaksha hub) {
    super(hub, Space.class);
  }

  @Override
  protected @NotNull Response validateFeature(@NotNull Write featureCodec) {
    if (EWriteOp.DELETE.toString().equals(featureCodec.getOp())) {
      return new SuccessResponse();
    }
    Response basicValidation = super.validateFeature(featureCodec);
    if (basicValidation instanceof ErrorResponse) {
      return basicValidation;
    }
    Space space = (Space) featureCodec.getFeature();
    return handlerExistenceValidation(space);
  }

  private @NotNull Response handlerExistenceValidation(Space space) {
    List<String> missingHandlerIds = getMissingHandlersFor(space);
    if (missingHandlerIds.isEmpty()) {
      return new SuccessResponse();
    } else {
      return new ErrorResponse(
              NakshaError.NOT_FOUND,
          "Following handlers defined for Space %s don't exist: %s"
                  .formatted(space.getId(), String.join(",", missingHandlerIds)),
              null,
              null);
    }
  }

  private List<String> getMissingHandlersFor(Space space) {
    List<String> expectedHandlerIds = space.getEventHandlerIds();
    ReadFeatures getEventHandlersRequest = readFeaturesByIdsRequest(EVENT_HANDLERS, expectedHandlerIds);
    IReadSession readSession =
            nakshaHub().getSpaceStorage().newReadSession(SessionOptions.from(currentContext(), false));
    Response result = readSession.execute(getEventHandlersRequest);
    return missingHandlersIds(result, expectedHandlerIds);
  }

  private List<String> missingHandlersIds(Response fetchedHandlers, List<String> expectedHandlersIds) {
    List<String> availableHandlerIds = readIdsFromResult(fetchedHandlers);
    return expectedHandlersIds.stream()
        .filter(expectedId -> !availableHandlerIds.contains(expectedId))
        .toList();
  }
}
