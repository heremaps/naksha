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
package com.here.naksha.lib.handlers;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.*;
import static com.here.naksha.lib.handlers.util.RequestTypesUtil.isOnlyWriteFeatures;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.Space;

import java.util.List;

import naksha.auth.ServiceUserRights;
import naksha.auth.UserRightsMatrix;
import naksha.base.NakshaError;
import naksha.model.NakshaContext;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationEventHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(AuthorizationEventHandler.class);
  protected @Nullable Space space;
  protected @Nullable List<EventHandlerConfig> eventHandlers;

  public static final String SERVICE_NAKSHA = "naksha";
  public static final String SERVICE_XYZ_HUB = "xyz-hub";
  public static final String ACTION_READ_FEATURES = "readFeatures";

  public AuthorizationEventHandler(final @NotNull INaksha hub) {
    super(hub);
  }

  public AuthorizationEventHandler(
          final @NotNull INaksha hub, final @NotNull Space space, final @NotNull List<EventHandlerConfig> eventHandlers) {
    super(hub);
    this.space = space;
    this.eventHandlers = eventHandlers;
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (isOnlyWriteFeatures(request)) {
      return PROCESS;
    }
    return SEND_UPSTREAM_WITHOUT_PROCESSING;
  }

  @Override
  protected @NotNull Response process(@NotNull IEvent event) {
    final Request request = event.getRequest();
    final NakshaContext ctx = NakshaContext.currentContext();

    logger.info("Handler received request {}", request.getClass().getSimpleName());

    if (ctx.getSu()) {
      return event.sendUpstream();
    }

    final UserRightsMatrix userRightsMatrix = ctx.getUrm();
    ServiceUserRights serviceRights = null;

    if (userRightsMatrix != null) {
      serviceRights = userRightsMatrix.get(SERVICE_NAKSHA);

      if (serviceRights == null) {
        serviceRights = userRightsMatrix.get(SERVICE_XYZ_HUB);
      }
    }

    if (hasNonReadFeatureAction(serviceRights)) {
      return event.sendUpstream();
    }

    return new ErrorResponse(
            NakshaError.FORBIDDEN, "You do not have the required permissions to perform this action.", null);
  }

  private boolean hasNonReadFeatureAction(@Nullable ServiceUserRights matrix) {
    if (matrix == null || matrix.isEmpty()) {
      return false;
    }
    // If any action other than readFeatures exists, allow.
    return matrix.keySet().stream().anyMatch(action -> !ACTION_READ_FEATURES.equals(action));

  }
}
