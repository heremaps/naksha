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
import static com.here.naksha.lib.handlers.util.MockUtil.parseFeatures;
import static com.here.naksha.lib.handlers.util.MockUtil.parseJson;
import static com.here.naksha.lib.handlers.util.MockUtil.toJson;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.ContextWriteFeatures;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.HandlerUtil;
import java.util.ArrayList;
import java.util.List;

import naksha.model.objects.NakshaFeatureList;
import naksha.mom.v2.MomProperties;
import naksha.mom.v2.MomReference;
import naksha.mom.v2.MomReferenceList;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.Request;
import naksha.model.request.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockValidationHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(MockValidationHandler.class);
  protected @NotNull EventHandlerConfig eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull NakshaProperties properties;

  private static final String MOCK_VIOLATIONS_FILE = "mock_data/dry_run_violations.json";
  private static final NakshaFeatureList mockViolations = parseFeatures(MOCK_VIOLATIONS_FILE);
  private static final int totalViolations = mockViolations.size();

  public MockValidationHandler(
      final @NotNull EventHandlerConfig eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties = eventHandler.getProperties(NakshaProperties.TYPE);
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
        request, ContextWriteFeatures.class, "Unsupported request type for validation");

    final @Nullable List<NakshaFeature> violations = validateFeatures(cwf, cwf.getContext());

    // create and forward request for next handler in the pipeline
    final ContextWriteFeatures upstreamRequest =
        HandlerUtil.createContextWriteRequestFromWriteList(cwf.getWrites(), cwf.getContext(), violations);
    return event.sendUpstream(upstreamRequest);
  }

  protected @Nullable List<NakshaFeature> validateFeatures(
      final @NotNull ContextWriteFeatures cwf, final @Nullable List<?> context) {
    // For random features from the input list, create 0-to-N random violations
    final List<NakshaFeature> violations;

    // Decide randomly whether to generate violations or not
    // Generation violations if odd number of features supplied, otherwise not
    final boolean generateViolation = (cwf.getWrites().size() % 2) > 0;

    if (!generateViolation) {
      return null;
    }

    // TODO : Write validation logic.

    // Generate random violations and attach feature references
    violations = new ArrayList<>();
    int featureCnt = 0;
    final List<NakshaFeature> features = HandlerUtil.getFeaturesFromWriteList(cwf.getWrites());
    for (final NakshaFeature feature : features) {
      featureCnt++;
      // Distribution of "count" of violations, depends on feature "number",
      // using min condition (i.e. min (feature, violation count))
      // For example, if we have 4 features and 3 mock violations, then:
      //    feature #1, will have 1 violation i.e. min(1,3)
      //    feature #2, will have 2 violations i.e. min(2,3)
      //    feature #3, will have 3 violations i.e. min(3,3)
      //    feature #4, will have 3 violations i.e. min(4,3)
      int violationsCount = Math.min(featureCnt, totalViolations);
      final Object momType = feature.get("momType");
      violations.addAll(getNViolationsWithFeatureReference(
          violationsCount,
          feature,
          cwf.getWrites().get(featureCnt - 1).getCollectionId(),
          (momType == null) ? "" : momType.toString()));
    }
    return violations;
  }

  private @NotNull List<NakshaFeature> getNViolationsWithFeatureReference(
      final int count,
      final @NotNull NakshaFeature feature,
      final @NotNull String spaceId,
      final @Nullable String featureType) {
    final List<NakshaFeature> violations = new ArrayList<>();
    for (int i = 0; i < count && i < totalViolations; i++) {
      final var violation = mockViolations.get(i);
      assert violation != null;
      // randomize violation id
      violation.setId("urn:here::here:Topology:violation_" + RandomStringUtils.randomAlphabetic(12));
      // add reference to feature
      final MomReference reference = new MomReference(feature.getId(), spaceId, featureType);
      final MomReferenceList referenceList = new MomReferenceList();
      referenceList.add(reference);
      violation.getProperties(MomProperties.TYPE).setReferences(referenceList);
      violation.put("violatedObject", reference);
      violation.setGeometry(feature.getGeometry());
      // add violation to the list
      violations.add(violation);
    }
    return violations;
  }
}
