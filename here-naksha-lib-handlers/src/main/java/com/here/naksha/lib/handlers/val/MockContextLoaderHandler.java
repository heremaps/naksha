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

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.ContextWriteXyzFeatures;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.HandlerUtil;
import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import naksha.base.JvmProxyUtil;
import naksha.model.NakshaError;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.*;

public class MockContextLoaderHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockContextLoaderHandler.class);
  protected @NotNull EventHandler eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull NakshaProperties properties;

  public MockContextLoaderHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties =
            Objects.requireNonNull(JvmProxyUtil.box(eventHandler.getProperties(), NakshaProperties.class));
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (RequestTypesUtil.isOnlyWriteFeatures(request)) {
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
  public @NotNull Response process(@NotNull IEvent event) {
    final Request request = event.getRequest();

    logger.info("Handler received request {}", request.getClass().getSimpleName());

    try {
      if (!(RequestTypesUtil.isOnlyWriteFeatures(request))) {
        throw new XyzErrorException(new NakshaError(
                NakshaError.NOT_IMPLEMENTED,
                "Unsupported request type for validation - "
                        + request.getClass().getSimpleName()));
      }
      final WriteRequest writeRequest = (WriteRequest) request;

      // Generate Validate request
      final Request forwardRequest = generateContextRequest(writeRequest);
      return event.sendUpstream(forwardRequest);
    } catch (XyzErrorException erx) {
      logger.warn("Error processing validation request. ", erx);
      return new ErrorResponse(erx.nakshaError);
    }
  }

  protected @NotNull Request generateContextRequest(final @NotNull WriteRequest wf) {
    // prepare ContextWriteFeatures request
    final ContextWriteXyzFeatures contextWriteFeatures = new ContextWriteXyzFeatures();
    // Add features in the request
    if (wf.getWrites().isEmpty()) {
      throw new XyzErrorException(NakshaError.ILLEGAL_ARGUMENT, "No features supplied for validation");
    }
    for (final Write write : wf.getWrites()) {
      if (!WriteOp.UPSERT.equals(write.getOp())) {
        throw new XyzErrorException(
                NakshaError.NOT_IMPLEMENTED, "Unsupported operation type for validation - " + write.getOp());
      }
      HandlerUtil.checkInstanceOf(
              write.getFeature(), NakshaFeature.class, "Unsupported feature type for validation");
      contextWriteFeatures.add(write);
    }
    // TODO : Load and populate context (features) in request

    return contextWriteFeatures;
  }
}
