/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

import static com.here.naksha.lib.core.NakshaAdminCollection.EVENT_HANDLERS;
import static com.here.naksha.lib.core.NakshaContext.currentContext;
import static com.here.naksha.lib.core.util.storage.RequestHelper.readFeaturesByIdsRequest;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.handlers.exceptions.MissingHandlersException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class IntHandlerForSpaces extends AdminFeatureEventHandler<Space> {

  public IntHandlerForSpaces(final @NotNull INaksha hub) {
    super(hub, Space.class);
  }

  @Override
  protected @NotNull Result validateFeature(@NotNull XyzFeatureCodec featureCodec) {
    if (EWriteOp.DELETE.toString().equals(featureCodec.getOp())) {
      return new SuccessResult();
    }
    Result basicValidation = super.validateFeature(featureCodec);
    if (basicValidation instanceof ErrorResult) {
      return basicValidation;
    }
    Space space = (Space) featureCodec.getFeature();
    return handlerExistenceValidation(space);
  }

  private @NotNull Result handlerExistenceValidation(Space space) {
    List<String> missingHandlerIds = getMissingHandlersFor(space);
    if (missingHandlerIds.isEmpty()) {
      return new SuccessResult();
    } else {
      return new MissingHandlersException(space.getId(), missingHandlerIds).toErrorResult();
    }
  }

  private List<String> getMissingHandlersFor(Space space) {
    List<String> expectedHandlerIds = space.getEventHandlerIds();
    ReadFeatures getEventHandlersRequest = readFeaturesByIdsRequest(EVENT_HANDLERS, expectedHandlerIds);
    try (IReadSession readSession = nakshaHub().getAdminStorage().newReadSession(currentContext(), false)) {
      try (Result result = readSession.execute(getEventHandlersRequest)) {
        return missingHandlersIds(result, expectedHandlerIds);
      }
    }
  }

  private List<String> missingHandlersIds(Result fetchedHandlers, List<String> expectedHandlersIds) {
    Set<String> availableHandlers = new HashSet<>(expectedHandlersIds.size());
    try (ForwardCursor<XyzFeature, XyzFeatureCodec> cursor = fetchedHandlers.getXyzFeatureCursor()) {
      while (cursor.hasNext()) {
        cursor.next();
        availableHandlers.add(cursor.getId());
      }
    } catch (NoCursor noCursor) {
      return expectedHandlersIds;
    }
    return expectedHandlersIds.stream()
        .filter(expectedId -> !availableHandlers.contains(expectedId))
        .toList();
  }
}
