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

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.storage.ContextWriteXyzFeatures;
import com.here.naksha.lib.handlers.util.PropertyOperationUtil;
import naksha.geo.XyzProperties;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import naksha.model.request.query.IPropertyQuery;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SEND_UPSTREAM_WITHOUT_PROCESSING;

public class SourceIdHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SourceIdHandler.class);
  private static final String TAG_PREFIX = "naksha_source_id_"; //TODO decide
  private static final String SOURCE_ID = "sourceId";
  public static final int PREF_PATHS_SIZE = 3;

  public SourceIdHandler(
          final @NotNull INaksha hub) {
    super(hub);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (request instanceof ReadFeatures || request instanceof WriteRequest) {
      return PROCESS;
    }
    return SEND_UPSTREAM_WITHOUT_PROCESSING;
  }

  @Override
  public @NotNull Response process(@NotNull IEvent event) {
    final Request request = event.getRequest();
    logger.info("Handler received request {}", request.getClass().getSimpleName());
    if (request instanceof ReadFeatures readRequest) {
      // Read request
      transformPropertyOperation(readRequest);
    } else if (request instanceof WriteRequest wr) {
      // Write request
      WriteList codecList = wr.getWrites();
      if (wr instanceof ContextWriteXyzFeatures cwf) {
        codecList = cwf.getWrites();
      }
      if (!codecList.isEmpty()) {
        codecList.stream()
                .map(Write::getFeature)
            .filter(Objects::nonNull)
            .forEachOrdered(this::setSourceIdTags);
      }
    }

    return event.sendUpstream(request);
  }

  private void transformPropertyOperation(ReadFeatures readRequest) {

    if (readRequest.getQuery().getProperties() == null) {
      return;
    }

    IPropertyQuery propertyOp = readRequest.getQuery().getProperties();

    if (propertyOp.children() != null && !propertyOp.children().isEmpty()) {
      PropertyOperationUtil.transformPropertyInPropertyOperationTree(
          propertyOp, SourceIdHandler::mapIntoTagOperation);
    } else {
      mapIntoTagOperation(propertyOp).ifPresent(readRequest::setPropertyOp);
    }
  }

  private void setSourceIdTags(NakshaFeature feature) {
    NakshaProperties properties = feature.getProperties();
    getSourceIdFromFeature(properties).ifPresent(sourceId -> updateTagsWithSourceIdProperty(properties, sourceId));
  }

  private void updateTagsWithSourceIdProperty(XyzProperties properties, String sourceId) {
    properties.getXyzNamespace().removeTagsWithPrefix(TAG_PREFIX);
    properties.getXyzNamespace().addTag(TAG_PREFIX + sourceId, false);
  }

  private Optional<String> getSourceIdFromFeature(XyzProperties properties) {
    try {
      return Optional.ofNullable(properties.get(XyzProperties.HERE_META_NS))
          .map(Map.class::cast)
          .map(metaProperties -> metaProperties.get(SOURCE_ID))
          .map(Object::toString);
    } catch (ClassCastException exception) {
      return Optional.empty();
    }
  }

  public static Optional<POp> mapIntoTagOperation(POp propertyOperation) {

    if (sourceIdTransformationCapable(propertyOperation) && operationTypeAllowed(propertyOperation)) {
      return Optional.of(POp.exists(PRef.tag(TAG_PREFIX + propertyOperation.getValue())));
    }

    return Optional.empty();
  }

  private static boolean propertyReferenceEqualsSourceId(PRef pRef) {
    List<@NotNull String> path = pRef.getPath();
    return path.size() == PREF_PATHS_SIZE
        && path.containsAll(List.of(XyzFeature.PROPERTIES, XyzProperties.HERE_META_NS, SOURCE_ID));
  }

  private static boolean sourceIdTransformationCapable(POp propertyOperation) {
    return propertyReferenceEqualsSourceId(propertyOperation.getPropertyRef())
        && propertyOperation.getValue() != null
        && propertyOperation.children() == null;
  }

  private static boolean operationTypeAllowed(POp propertyOperation) {
    return propertyOperation.op().equals(POpType.EQ)
        || propertyOperation.op().equals(POpType.CONTAINS);
  }
}
