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
package com.here.naksha.lib.handlers.val;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SEND_UPSTREAM_WITHOUT_PROCESSING;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import naksha.model.XyzFeature;
import naksha.geo.XyzProperties;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.ContextWriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import naksha.model.ErrorResult;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import naksha.model.ReadFeatures;
import naksha.model.Request;
import com.here.naksha.lib.core.models.storage.Result;
import naksha.model.WriteFeatures;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.HandlerUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockContextLoaderHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockContextLoaderHandler.class);
  protected @NotNull EventHandler eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull XyzProperties properties;

  public MockContextLoaderHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties = JsonSerializable.convert(eventHandler.getProperties(), XyzProperties.class);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request<?> request = event.getRequest();
    if (request instanceof WriteFeatures<?, ?, ?>) {
      return PROCESS;
    }
    if (request instanceof ReadFeatures) {
      return SUCCEED_WITHOUT_PROCESSING;
    }
    return SEND_UPSTREAM_WITHOUT_PROCESSING;
  }

  /**
   * The method invoked by the event-pipeline to process custom Storage specific read/write operations
   *
   * @param event the event to process.
   * @return the result.
   */
  @Override
  public @NotNull Result process(@NotNull IEvent event) {
    final Request<?> request = event.getRequest();

    logger.info("Handler received request {}", request.getClass().getSimpleName());

    try {
      final WriteFeatures<?, ?, ?> writeRequest = HandlerUtil.checkInstanceOf(
          request, WriteFeatures.class, "Unsupported request type for validation");

      // Generate Validate request
      final Request<?> forwardRequest = generateContextRequest(writeRequest);
      return event.sendUpstream(forwardRequest);
    } catch (XyzErrorException erx) {
      logger.warn("Error processing validation request. ", erx);
      return new ErrorResult(erx.xyzError, erx.getMessage());
    }
  }

  protected @NotNull Request<?> generateContextRequest(final @NotNull WriteFeatures<?, ?, ?> wf) {
    // prepare ContextWriteFeatures request
    final ContextWriteXyzFeatures contextWriteFeatures = new ContextWriteXyzFeatures(wf.getCollectionId());
    // Add features in the request
    if (wf.features.isEmpty()) {
      throw new XyzErrorException(XyzError.ILLEGAL_ARGUMENT, "No features supplied for validation");
    }
    for (final FeatureCodec<?, ?> codec : wf.features) {
      if (!EWriteOp.PUT.toString().equals(codec.getOp())) {
        throw new XyzErrorException(
            XyzError.NOT_IMPLEMENTED, "Unsupported operation type for validation - " + codec.getOp());
      }
      final XyzFeature feature = HandlerUtil.checkInstanceOf(
          codec.getFeature(), XyzFeature.class, "Unsupported feature type for validation");
      contextWriteFeatures.add(EWriteOp.get(codec.getOp()), feature);
    }
    // TODO : Load and populate context (features) in request

    return contextWriteFeatures;
  }
}
