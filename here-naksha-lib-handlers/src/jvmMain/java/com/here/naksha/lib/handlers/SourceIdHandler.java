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

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SEND_UPSTREAM_WITHOUT_PROCESSING;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.storage.ContextWriteXyzFeatures;
import java.util.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import naksha.model.request.query.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceIdHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SourceIdHandler.class);
  private static final String TAG_PREFIX = "xyz_source_id_";
  private static final String SOURCE_ID = "sourceId";
  public static final int PREF_PATHS_SIZE = 3;

  public SourceIdHandler(final @NotNull INaksha hub) {
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
      mapIntoTagOperation(readRequest);
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

  /**
   * For the AND case, any property query sub-clause that can be converted into tag query will be converted and returned, leaving the remaining inconvertible clauses intact.
   * <br>
   * For the OR case, it is required that every sub-clause must be convertible, else the whole clause will not be converted.
   * This is because OR relation between types of queries (property, tag, spatial,...) is not supported, only AND is supported and applied at the very end of the request.
   */
  public static void mapIntoTagOperation(ReadFeatures readRequest) {

    if (readRequest.getQuery().getProperties() == null) {
      return;
    }

    IPropertyQuery propertyOp = readRequest.getQuery().getProperties();

    final Optional<ITagQuery> tagQuery = transformPropertyOperation(propertyOp);
    tagQuery.ifPresent(presentTagQuery -> {
      // Set tag query to request, combining with the already existing tag query if given
      final ITagQuery existingTagQuery = readRequest.getQuery().getTags();
      if (existingTagQuery != null) {
        readRequest.getQuery().setTags(new TagAnd(presentTagQuery, existingTagQuery));
      } else {
        readRequest.getQuery().setTags(presentTagQuery);
      }
      // Clean up property query if it has already been transformed into tag query
      if (isFullyConvertedToITagQuery(propertyOp)) {
        readRequest.getQuery().setProperties(null);
      }
      // Unwrap the query if it is an AND clause with only 1 remaining sub-clause
      else if ((propertyOp instanceof PAnd canBeSimplified) && (canBeSimplified.size() == 1)) {
        readRequest.getQuery().setProperties(canBeSimplified.get(0));
      }
    });
  }

  private void setSourceIdTags(NakshaFeature feature) {
    NakshaProperties properties = feature.getProperties();
    getSourceIdFromFeature(properties).ifPresent(sourceId -> updateTagsWithSourceIdProperty(properties, sourceId));
  }

  private void updateTagsWithSourceIdProperty(NakshaProperties properties, String sourceId) {
    properties.getXyz().removeTagsWithPrefix(TAG_PREFIX);
    properties.getXyz().addTag(TAG_PREFIX + sourceId, false);
  }

  private Optional<String> getSourceIdFromFeature(NakshaProperties properties) {
    try {
      return Optional.ofNullable(properties.get(NakshaProperties.META_KEY))
          .map(Map.class::cast)
          .map(metaProperties -> metaProperties.get(SOURCE_ID))
          .map(Object::toString);
    } catch (ClassCastException exception) {
      return Optional.empty();
    }
  }

  private static boolean isFullyConvertedToITagQuery(IPropertyQuery propertyQuery) {
    return (propertyQuery instanceof PQuery)
        || (propertyQuery instanceof PNot)
        || (propertyQuery instanceof POr)
        || ((propertyQuery instanceof PAnd pAnd) && (pAnd.isEmpty()));
  }

  private static Optional<ITagQuery> transformPropertyOperation(IPropertyQuery propertyOperation) {
    if (propertyOperation instanceof PAnd pAnd) {
      final TagAnd tagAnd = new TagAnd();
      // List of successfully transformed property queries to be removed at the end, so as not to disrupt the loop
      final List<IPropertyQuery> toRemove = new ArrayList<>();
      final int size = pAnd.size();
      for (int i = 0; i < size; i++) {
        final IPropertyQuery propertyComponent = pAnd.get(i);
        final Optional<ITagQuery> tagComponent = transformPropertyOperation(propertyComponent);
        if (tagComponent.isPresent()) {
          tagAnd.add(tagComponent.get());
          if (isFullyConvertedToITagQuery(propertyComponent)) {
            toRemove.add(propertyComponent);
          }
          // Unwrap the query if it is an AND clause with only 1 remaining sub-clause
          else if ((propertyComponent instanceof PAnd canBeSimplified) && (canBeSimplified.size() == 1)) {
            pAnd.set(i, canBeSimplified.get(0));
          }
        }
      }
      pAnd.removeAll(toRemove);
      if (tagAnd.isEmpty()) {
        return Optional.empty();
      } else if (tagAnd.size() == 1) {
        // Unwrap this one single query in an AND clause
        return Optional.of(tagAnd.get(0));
      }
      return Optional.of(tagAnd);
    } else if (propertyOperation instanceof POr pOr) {
      final TagOr tagOr = new TagOr();
      for (IPropertyQuery iPropertyQuery : pOr) {
        final Optional<ITagQuery> tagComponent = transformPropertyOperation(iPropertyQuery);
        if (tagComponent.isEmpty()) {
          // At least one sub-clause in an OR clause cannot be converted, hence abort and leave the whole OR
          // as is
          return Optional.empty();
        }
        tagOr.add(tagComponent.get());
      }
      return Optional.of(tagOr);
    } else if (propertyOperation instanceof PNot pNot) {
      final Optional<ITagQuery> tagComponent = transformPropertyOperation(pNot.getQuery());
      return tagComponent.map(TagNot::new);
    } else if (propertyOperation instanceof PQuery pQuery) {
      if (sourceIdTransformationCapable(pQuery) && operationTypeAllowed(pQuery)) {
        final TagExists tagQuery = new TagExists(TAG_PREFIX + pQuery.getValue());
        return Optional.of(tagQuery);
      }
      return Optional.empty();
    } else {
      throw new IllegalArgumentException("Unknown property operation type: "
          + propertyOperation.getClass().getSimpleName());
    }
  }

  private static boolean propertyReferenceEqualsSourceId(Property pRef) {
    List<@NotNull String> path = pRef.getPath();
    return path.size() == PREF_PATHS_SIZE && path.containsAll(List.of(NakshaProperties.META_KEY, SOURCE_ID));
  }

  private static boolean sourceIdTransformationCapable(PQuery propertyOperation) {
    return propertyReferenceEqualsSourceId(propertyOperation.getProperty()) && propertyOperation.getValue() != null;
  }

  private static boolean operationTypeAllowed(PQuery propertyOperation) {
    final AnyOp op = propertyOperation.getOp();
    return StringOp.EQUALS.equals(op) || StringOp.CONTAINS.equals(op) || DoubleOp.EQ.equals(op);
  }
}
