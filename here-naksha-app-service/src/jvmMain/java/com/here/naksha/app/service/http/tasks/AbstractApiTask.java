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
package com.here.naksha.app.service.http.tasks;

import static com.here.naksha.common.http.apis.ApiParamsConst.DEF_ADMIN_FEATURE_LIMIT;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static naksha.base.JvmBoxingUtil.box;
import static naksha.model.util.ResultHelper.extractResponseItems;
import static naksha.model.util.ResultHelper.readFeatureFromResponse;

import com.here.naksha.app.service.http.HttpResponseType;
import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.tasks.processor.FeaturePostProcessor;
import com.here.naksha.app.service.models.IterateHandle;
import com.here.naksha.lib.core.AbstractTask;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.ContextXyzFeatureResponse;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import naksha.base.PAnyMap;
import naksha.base.FromJsonOptions;
import naksha.base.Base;
import naksha.base.Action;
import naksha.base.fn.Fn1;
import naksha.model.*;
import naksha.base.NakshaError;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract class that can be used for all Http API specific custom Task implementations.
 */
public abstract class AbstractApiTask<T extends XyzResponse>
    extends AbstractTask<XyzResponse, AbstractApiTask<T>> {

  private static final Logger logger = LoggerFactory.getLogger(AbstractApiTask.class);

  protected final @NotNull RoutingContext routingContext;
  protected final @NotNull NakshaHttpVerticle verticle;

  /**
   * Creates a new task.
   *
   * @param nakshaHub     The reference to the NakshaHub.
   * @param nakshaContext The reference to the NakshContext
   */
  protected AbstractApiTask(
      final @NotNull NakshaHttpVerticle verticle,
      final @NotNull INaksha nakshaHub,
      final @NotNull RoutingContext routingContext,
      final @NotNull NakshaContext nakshaContext) {
    super(nakshaHub, nakshaContext);
    this.verticle = verticle;
    this.routingContext = routingContext;
  }

  protected @NotNull XyzResponse errorResponse(@NotNull Throwable throwable) {
    logger.warn("The task failed with an exception. ", throwable);
    return verticle.sendErrorResponse(
        routingContext, new NakshaError(NakshaError.EXCEPTION, "Task failed processing! " + throwable.getMessage(), throwable));
  }

  public @NotNull XyzResponse executeUnsupported() {
    return verticle.sendErrorResponse(routingContext, NakshaError.NOT_IMPLEMENTED, "Unsupported operation!");
  }

  protected <F extends NakshaFeature> @NotNull XyzResponse transformResponseToXyzFeatureResponse(
      final @Nullable Response response,
      final @NotNull Class<F> type,
      final @NotNull NoElementsStrategy noElementsStrategy
  ) {
    return transformResponseToXyzFeatureResponse(response, type, noElementsStrategy, null);
  }

  protected XyzResponse handleNoElements(@NotNull NoElementsStrategy noElementsStrategy) {
    return verticle.sendErrorResponse(routingContext, noElementsStrategy.nakshaError);
  }

  protected <F extends NakshaFeature> @NotNull XyzResponse transformResponseToXyzFeatureResponse(
      final @Nullable Response response,
      final @NotNull Class<F> type,
      final @NotNull NoElementsStrategy noElementsStrategy,
      final @Nullable FeaturePostProcessor<F> featurePostProcessor
  ) {
    final XyzResponse validatedErrorResponse = validateErrorResult(response);
    if (validatedErrorResponse != null) {
      return validatedErrorResponse;
    } else if (response instanceof SuccessResponse successResponse) {
      F feature = readFeatureFromResponse(successResponse, type);
      if (feature != null && featurePostProcessor != null) {
        feature = featurePostProcessor.postProcess(feature);
      }
      if (feature == null) {
        return handleNoElements(noElementsStrategy);
      }
      final XyzFeatureCollection featureResponse = new XyzFeatureCollection().withFeatures(List.of(feature));
      return verticle.sendXyzResponse(routingContext, HttpResponseType.FEATURE, featureResponse);
    } else {
      return verticle.sendErrorResponse(
          routingContext,
          NakshaError.EXCEPTION,
          "Unable to process unexpected response: " + response
      );
    }
  }

  protected <F extends NakshaFeature> @NotNull XyzResponse transformResponseToXyzCollectionResponse(
      final @Nullable Response response,
      final @NotNull Class<F> type,
      final @Nullable FeaturePostProcessor<F> featurePostProcessor
  ) {
    return transformResponseToXyzCollectionResponse(
        response, type, 0, DEF_ADMIN_FEATURE_LIMIT, null, featurePostProcessor);
  }

  protected <F extends NakshaFeature> @NotNull XyzResponse transformResponseToXyzCollectionResponse(
      final @Nullable Response response,
      final @NotNull Class<F> type,
      final int offset,
      final int maxLimit,
      final @Nullable IterateHandle handle,
      final @Nullable FeaturePostProcessor<F> featurePostProcessor) {
    final XyzResponse validatedErrorResponse = validateErrorResultEmptyCollection(response);
    if (validatedErrorResponse != null) {
      return validatedErrorResponse;
    } else if (response instanceof SuccessResponse successResponse) {
      final List<F> features = extractResponseItems(successResponse, type, offset, maxLimit);
      List<F> processedFeatures = features;
      if (featurePostProcessor != null) {
        processedFeatures = new ArrayList<>();
        for (F feature : features) {
          final F processedFeature = featurePostProcessor.postProcess(feature);
          if (processedFeature != null) {
            processedFeatures.add(processedFeature);
          }
        }
      }
      if (processedFeatures.isEmpty()) {
        logger.info("No features found, returning empty collection");
        return verticle.sendXyzResponse(
            routingContext, HttpResponseType.FEATURE_COLLECTION, emptyFeatureCollection());
      }
      // Populate handle (if provided), with the values ready for next iteration
      final String handleStr = getIterateHandleAsString(processedFeatures.size(), offset, maxLimit, handle);
      // TODO: CASL-681 failes because of missing inserts
      return verticle.sendXyzResponse(
          routingContext,
          HttpResponseType.FEATURE_COLLECTION,
          new XyzFeatureCollection()
              .withFeatures(processedFeatures)
              .withNextPageToken(handleStr));
    } else {
      return verticle.sendErrorResponse(
          routingContext,
          NakshaError.EXCEPTION,
          "Unable to process unexpected response: " + response
      );
    }
  }

  private static String getIterateHandleAsString(
      int featuresFound, int crtOffset, int maxLimit, final @Nullable IterateHandle handle) {
    // nothing to populate if handle is not provided OR if we don't have more features to iterate
    if (handle == null || featuresFound < maxLimit) {
      return null;
    }
    handle.setOffset(crtOffset + featuresFound); // set offset for next iteration
    handle.setLimit(maxLimit);
    return handle.base64EncodedSerializedJson();
  }

  protected @NotNull XyzResponse transformWriteResultToXyzCollectionResponse(
      final @Nullable Response response,
      final boolean isDeleteOperation,
      @Nullable FeaturePostProcessor<NakshaFeature> postProcessor
  ) {
    final XyzResponse validatedErrorResponse = validateErrorResult(response);
    if (validatedErrorResponse != null) {
      return validatedErrorResponse;
    } else if (response instanceof SuccessResponse successResponse) {
      final Map<Action, List<NakshaFeature>> featureMap = postProcessedFeaturesByAction(successResponse, postProcessor);
      final List<NakshaFeature> insertedFeatures = featureMap.get(Action.CREATE);
      final List<NakshaFeature> updatedFeatures = featureMap.get(Action.UPDATE);
      final List<NakshaFeature> deletedFeatures = featureMap.get(Action.DELETE);
      // extract violations if available
      List<NakshaFeature> violations = null;
      if (successResponse instanceof ContextXyzFeatureResponse cr) {
        violations = cr.getViolations();
      }
      if (featureMap.isEmpty() && (violations == null || violations.isEmpty())) {
        if (isDeleteOperation) {
          logger.info("No data found, returning empty collection");
          return verticle.sendXyzResponse(
              routingContext, HttpResponseType.FEATURE_COLLECTION, emptyFeatureCollection());
        }
        return verticle.sendErrorResponse(
            routingContext, NakshaError.EXCEPTION, "Unexpected empty response");
      }
      return verticle.sendXyzResponse(
          routingContext,
          HttpResponseType.FEATURE_COLLECTION,
          new XyzFeatureCollection()
              .withInsertedFeatures(insertedFeatures)
              .withUpdatedFeatures(updatedFeatures)
              .withDeletedFeatures(deletedFeatures)
              .withViolations(violations));
    } else {
      return verticle.sendErrorResponse(
          routingContext,
          NakshaError.EXCEPTION,
          "Unable to process unexpected response: " + response
      );
    }
  }

  protected @NotNull Response executeReadRequestFromSpaceStorage(@NotNull ReadRequest request) {
    return naksha()
        .getSpaceStorage()
        .useReadSession(SessionOptions.from(context(), false), reader -> reader.execute(request));
  }

  protected @NotNull Response executeReadRequestFromSpaceStorage(@NotNull Fn1<@NotNull ReadRequest, @NotNull IReadSession> requestBuilder) {
    return naksha()
      .getSpaceStorage()
      .useReadSession(SessionOptions.from(context(), false), reader -> reader.execute(requestBuilder.call(reader)));
  }

  protected @NotNull Response executeWriteRequestFromSpaceStorage(@NotNull WriteRequest request) {
    return naksha()
        .getSpaceStorage()
        .useWriteSession(SessionOptions.from(context(), true), writer -> writer.execute(request));
  }

  protected @NotNull Response executeWriteRequestFromSpaceStorage(@NotNull Fn1<@NotNull WriteRequest, @NotNull IWriteSession> requestBuilder) {
    return naksha()
      .getSpaceStorage()
      .useWriteSession(SessionOptions.from(context(), true), writer -> writer.execute(requestBuilder.call(writer)));
  }

  @NotNull XyzFeatureCollection emptyFeatureCollection() {
    return new XyzFeatureCollection().withFeatures(emptyList());
  }

  protected @Nullable XyzResponse validateErrorResultEmptyCollection(final @Nullable Response response) {
    if (response == null) {
      // return empty collection
      logger.warn("Unexpected null result, returning empty collection.");
      return verticle.sendXyzResponse(
          routingContext, HttpResponseType.FEATURE_COLLECTION, new XyzFeatureCollection());
    } else if (response instanceof ErrorResponse er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error response {}", er);
      return verticle.sendErrorResponse(routingContext, er.getError());
    }
    return null;
  }

  protected @Nullable XyzResponse validateErrorResult(final @Nullable Response response) {
    if (response == null) {
      // return empty collection
      logger.error("Unexpected null result!");
      return verticle.sendErrorResponse(routingContext, NakshaError.EXCEPTION, "Unexpected null result!");
    } else if (response instanceof ErrorResponse er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error result {}", er);
      return verticle.sendErrorResponse(routingContext, er.getError());
    }
    return null;
  }

  protected <P extends PAnyMap> @NotNull P parseRequestBodyAs(final Class<P> type) {
    final String bodyJson = routingContext.body().asString();
    return requireNonNull(box(Base.fromJSON(bodyJson, FromJsonOptions.DEFAULT), type));
  }

  /**
   * Helper method to fetch features from given Result and return a map of multiple lists of features with type T. Returned list is not
   * limited - to set the upper bound, use sibling method with limit argument.
   *
   * @param result the Result which is to be read
   * @return a map grouping the lists of features extracted from ReadResult (might be Map.empty())
   */
  private static Map<Action, List<NakshaFeature>> postProcessedFeaturesByAction(
      SuccessResponse result,
      FeaturePostProcessor<NakshaFeature> postProcessor
  ) {
    final NakshaFeatureList features = result.getFeatures();
    if (features.isEmpty()) {
      return Collections.emptyMap();
    }
    final List<NakshaFeature> insertedFeatures = new ArrayList<>();
    final List<NakshaFeature> updatedFeatures = new ArrayList<>();
    final List<NakshaFeature> deletedFeatures = new ArrayList<>();
    for (NakshaFeature feature : features) {
      postProcessor.postProcess(feature);
      final Action action = feature.getProperties().getXyz().getAction();
      if (action == Action.CREATE) {
        insertedFeatures.add(feature);
      } else if (action == Action.UPDATE) {
        updatedFeatures.add(feature);
      } else if (action == Action.DELETE) {
        deletedFeatures.add(feature);
      }
    }
    final Map<Action, List<NakshaFeature>> featuresByAction = new HashMap<>();
    featuresByAction.put(Action.CREATE, insertedFeatures);
    featuresByAction.put(Action.UPDATE, updatedFeatures);
    featuresByAction.put(Action.DELETE, deletedFeatures);
    return featuresByAction;
  }
}
