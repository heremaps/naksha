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
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.ContextWriteFeatures;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.HandlerUtil;
import java.util.List;
import java.util.Objects;
import naksha.base.JvmProxyUtil;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.Request;
import naksha.model.request.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(EchoHandler.class);
  protected @NotNull EventHandler eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull NakshaProperties properties;

  public EchoHandler(
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
    if (request instanceof ContextWriteFeatures) {
      return PROCESS;
    }
    return SUCCEED_WITHOUT_PROCESSING;
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
    final ContextWriteFeatures cwf = HandlerUtil.checkInstanceOf(
        request, ContextWriteFeatures.class, "Unsupported request type in echoHandler");

    // Extract Xyz features
    final List<NakshaFeature> features = HandlerUtil.getFeaturesFromWriteList(cwf.getWrites());

    // Extract Xyz context (list of features)
    final List<NakshaFeature> context = HandlerUtil.getXyzContextFromGenericList(cwf.getContext());

    // Extract Xyz violations (if to be persisted separately)
    final List<NakshaFeature> outputViolations = HandlerUtil.getViolationsFromGenericList(cwf.getViolations());

    // prepare result with op as UPDATED, as if features were persisted in DB
    return HandlerUtil.createContextResultFromFeatureList(features, context, outputViolations);
  }
}
