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

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.ContextWriteFeatures;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.HandlerUtil;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import naksha.base.JvmBoxingUtil;
import naksha.model.mom.MomReference;
import naksha.model.mom.MomReferenceList;
import naksha.model.mom.MomReviewState;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.Write;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndorsementHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(EndorsementHandler.class);
  protected @NotNull EventHandler eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull NakshaProperties properties;

  public EndorsementHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties =
        Objects.requireNonNull(JvmBoxingUtil.box(eventHandler.getProperties(), NakshaProperties.class));
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (request instanceof ContextWriteFeatures) {
      return PROCESS;
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

    final ContextWriteFeatures cwf = HandlerUtil.checkInstanceOf(
        request, ContextWriteFeatures.class, "Unsupported request type during endorsement");

    // Extract violations from request
    final List<NakshaFeature> violations = HandlerUtil.getViolationsFromGenericList(cwf.getViolations());

    // Mark each feature as AUTO_REVIEW_DEFERRED or UNPUBLISHED
    // (depending on whether there is associated violation or not)
    final List<NakshaFeature> updatedFeatures = HandlerUtil.getFeaturesFromWriteList(cwf.getWrites());
    for (final NakshaFeature feature : updatedFeatures) {
      updateFeatureDeltaStateIfMatchesViolations(feature, violations);
    }

    // TODO : Extract context (list of features) and make the violated ones part of the updatedFeatures
    // list, so they also get updated in storage

    // create and forward request for next handler in the pipeline
    final ContextWriteFeatures upstreamRequest = HandlerUtil.createContextWriteRequestFromFeatureList(
        cwf.getWrites().stream().map(Write::getCollectionId).collect(Collectors.toList()),
        updatedFeatures,
        cwf.getContext(),
        violations);
    return event.sendUpstream(upstreamRequest);
  }

  protected void updateFeatureDeltaStateIfMatchesViolations(
      final @NotNull NakshaFeature feature, final @Nullable List<NakshaFeature> violations) {
    HandlerUtil.setDeltaReviewState(feature, MomReviewState.UNPUBLISHED);
    if (violations == null) {
      return;
    }
    for (final NakshaFeature violation : violations) {
      final MomReferenceList references = violation.getProperties().getReferences();
      if (references == null) {
        continue;
      }
      for (final MomReference reference : references) {
        if (feature.getId().equals(reference.getId())) {
          HandlerUtil.setDeltaReviewState(feature, MomReviewState.AUTO_REVIEW_DEFERRED);
          return;
        }
      }
    }
  }
}
