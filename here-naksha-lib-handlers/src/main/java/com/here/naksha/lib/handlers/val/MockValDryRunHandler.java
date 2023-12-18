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
package com.here.naksha.lib.handlers.val;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventHandlerProperties;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.HandlerUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockValDryRunHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockValDryRunHandler.class);
  protected @NotNull EventHandler eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull EventHandlerProperties properties;

  protected @NotNull MockValidationHandler validationHandler;
  protected @NotNull EndorsementHandler endorsementHandler;
  protected @NotNull EchoHandler echoHandler;

  public MockValDryRunHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties = JsonSerializable.convert(eventHandler.getProperties(), EventHandlerProperties.class);
    // TODO : These handlers should be later added as part of full Validation & Endorsement pipeline
    this.validationHandler = new MockValidationHandler(eventHandler, hub, eventTarget);
    this.endorsementHandler = new EndorsementHandler(eventHandler, hub, eventTarget);
    this.echoHandler = new EchoHandler(eventHandler, hub, eventTarget);
  }

  /**
   * The method invoked by the event-pipeline to process custom Storage specific read/write operations
   *
   * @param event the event to process.
   * @return the result.
   */
  @Override
  public @NotNull Result processEvent(@NotNull IEvent event) {
    final NakshaContext ctx = NakshaContext.currentContext();
    final Request<?> origRequest = event.getRequest();
    Request<?> handlerRequest = null;
    Result handlerResult = null;

    logger.info("Handler received request {}", origRequest.getClass().getSimpleName());

    if (!(origRequest instanceof WriteFeatures<?, ?, ?> writeRequest))
      throw new XyzErrorException(
          XyzError.NOT_IMPLEMENTED,
          "Unsupported request type for validation dry run - "
              + origRequest.getClass().getSimpleName());

    try {
      // 1. Generate Validate request
      handlerRequest = generateValidateRequest(writeRequest);

      // 2. Perform mock validation
      handlerResult = validationHandler.validateHandler(handlerRequest);
      handlerRequest =
          HandlerUtil.createWriteContextRequestFromResult(writeRequest.getCollectionId(), handlerResult);

      // 3. Mark features as ENDORSED (if no violations) or AUTO_REVIEW_DEFERRED (in case of violations)
      handlerResult = endorsementHandler.endorsementHandler(handlerRequest);
      handlerRequest =
          HandlerUtil.createWriteContextRequestFromResult(writeRequest.getCollectionId(), handlerResult);

      // 4. Return ContextResultSet with features and violations
      handlerResult = echoHandler.echoHandler(handlerRequest);
      return handlerResult;
    } catch (XyzErrorException erx) {
      logger.warn("Error processing validation request. ", erx);
      return new ErrorResult(erx.xyzError, erx.getMessage());
    }
  }

  protected @NotNull Request<?> generateValidateRequest(final @NotNull WriteFeatures<?, ?, ?> wf) {
    // prepare ContextWriteFeatures request
    final ContextWriteXyzFeatures contextWriteFeatures = new ContextWriteXyzFeatures(wf.getCollectionId());
    // Add features in the request
    if (wf.features.isEmpty())
      throw new XyzErrorException(XyzError.ILLEGAL_ARGUMENT, "No features supplied for validation");
    for (final FeatureCodec<?, ?> codec : wf.features) {
      if (!EWriteOp.PUT.toString().equals(codec.getOp())) {
        throw new XyzErrorException(
            XyzError.NOT_IMPLEMENTED, "Unsupported operation type for validation - " + codec.getOp());
      }
      if (!(codec.getFeature() instanceof XyzFeature feature)) {
        throw new XyzErrorException(
            XyzError.NOT_IMPLEMENTED,
            "Unsupported feature type for validation - "
                + codec.getFeature().getClass().getSimpleName());
      }
      contextWriteFeatures.add(EWriteOp.get(codec.getOp()), feature);
    }
    // TODO : Add context (features) in request
    return contextWriteFeatures;
  }
}
