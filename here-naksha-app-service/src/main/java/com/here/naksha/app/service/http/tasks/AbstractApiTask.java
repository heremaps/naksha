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

import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.FAIL_ON_NO_ELEMENTS;
import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS;
import static com.here.naksha.common.http.apis.ApiParamsConst.DEF_ADMIN_FEATURE_LIMIT;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeatureFromResult;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.app.service.http.HttpResponseType;
import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.tasks.processor.FeaturePostProcessor;
import com.here.naksha.app.service.models.IterateHandle;
import com.here.naksha.lib.core.AbstractTask;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.storage.ContextXyzFeatureResult;
import com.here.naksha.lib.core.models.storage.EExecutedOp;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.WriteFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.view.ViewDeserialize;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract class that can be used for all Http API specific custom Task implementations.
 */
public abstract class AbstractApiTask<T extends XyzResponse>
    extends AbstractTask<XyzResponse, AbstractApiTask<XyzResponse>> {

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
        routingContext, XyzError.EXCEPTION, "Task failed processing! " + throwable.getMessage());
  }

  public @NotNull XyzResponse executeUnsupported() {
    return verticle.sendErrorResponse(routingContext, XyzError.NOT_IMPLEMENTED, "Unsupported operation!");
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzFeatureResponse(
      final @NotNull Result rdResult, final @NotNull Class<R> type) {
    return transformResultToXyzFeatureResponse(rdResult, type, NOT_FOUND_ON_NO_ELEMENTS, null);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzFeatureResponse(
      final @NotNull Result rdResult,
      final @NotNull Class<R> type,
      @Nullable FeaturePostProcessor<R> postProcessor) {
    return transformResultToXyzFeatureResponse(rdResult, type, NOT_FOUND_ON_NO_ELEMENTS, postProcessor);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformWriteResultToXyzFeatureResponse(
      final @Nullable Result wrResult, final @NotNull Class<R> type) {
    return transformResultToXyzFeatureResponse(wrResult, type, FAIL_ON_NO_ELEMENTS, null);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformWriteResultToXyzFeatureResponse(
      final @Nullable Result wrResult,
      final @NotNull Class<R> type,
      @Nullable FeaturePostProcessor<R> postProcessor) {
    return transformResultToXyzFeatureResponse(wrResult, type, FAIL_ON_NO_ELEMENTS, postProcessor);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformDeleteResultToXyzFeatureResponse(
      final @Nullable Result wrResult, final @NotNull Class<R> type) {
    return transformResultToXyzFeatureResponse(wrResult, type, NOT_FOUND_ON_NO_ELEMENTS, null);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformDeleteResultToXyzFeatureResponse(
      final @Nullable Result wrResult,
      final @NotNull Class<R> type,
      @Nullable FeaturePostProcessor<R> postProcessor) {
    return transformResultToXyzFeatureResponse(wrResult, type, NOT_FOUND_ON_NO_ELEMENTS, postProcessor);
  }

  protected XyzResponse handleNoElements(NoElementsStrategy noElementsStrategy) {
    return verticle.sendErrorResponse(routingContext, noElementsStrategy.xyzError, noElementsStrategy.message);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformResultToXyzFeatureResponse(
      final @Nullable Result result,
      final @NotNull Class<R> type,
      final @NotNull NoElementsStrategy noElementsStrategy,
      final @Nullable FeaturePostProcessor<R> postProcessor) {
    final XyzResponse validatedErrorResponse = validateErrorResult(result);
    if (validatedErrorResponse != null) {
      return validatedErrorResponse;
    } else {
      try {
        final R feature = readFeatureFromResult(result, type);
        R processedFeature = feature;
        if (feature != null && postProcessor != null) {
          processedFeature = postProcessor.postProcess(feature);
        }
        if (processedFeature == null) {
          return verticle.sendErrorResponse(
              routingContext,
              XyzError.NOT_FOUND,
              "No feature found for id "
                  + result.getXyzFeatureCursor().getId());
        }
        final List<R> featureList = new ArrayList<>();
        featureList.add(processedFeature);
        final XyzFeatureCollection featureResponse = new XyzFeatureCollection().withFeatures(featureList);
        return verticle.sendXyzResponse(routingContext, HttpResponseType.FEATURE, featureResponse);
      } catch (NoCursor | NoSuchElementException emptyException) {
        return handleNoElements(noElementsStrategy);
      }
    }
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzCollectionResponse(
      final @Nullable Result rdResult,
      final @NotNull Class<R> type,
      final @Nullable FeaturePostProcessor<R> featurePostProcessor) {
    return transformReadResultToXyzCollectionResponse(
        rdResult, type, 0, DEF_ADMIN_FEATURE_LIMIT, null, featurePostProcessor);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzCollectionResponse(
      final @Nullable Result rdResult, final @NotNull Class<R> type) {
    return transformReadResultToXyzCollectionResponse(rdResult, type, DEF_ADMIN_FEATURE_LIMIT);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzCollectionResponse(
      final @Nullable Result rdResult, final @NotNull Class<R> type, final long maxLimit) {
    return transformReadResultToXyzCollectionResponse(rdResult, type, 0, maxLimit, null, null);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzCollectionResponse(
      final @Nullable Result rdResult,
      final @NotNull Class<R> type,
      final long offset,
      final long maxLimit,
      final @Nullable IterateHandle handle,
      final @Nullable FeaturePostProcessor<R> featurePostProcessor) {
    final XyzResponse validatedErrorResponse = validateErrorResultEmptyCollection(rdResult);
    if (validatedErrorResponse != null) {
      return validatedErrorResponse;
    } else {
      try {
        final List<R> features = readFeaturesFromResult(rdResult, type, offset, maxLimit);
        List<R> processedFeatures = features;
        if (featurePostProcessor != null) {
          processedFeatures = new ArrayList<>();
          for (R feature : features) {
            final R processedFeature = featurePostProcessor.postProcess(feature);
            if (processedFeature != null) {
              processedFeatures.add(processedFeature);
            }
          }
        }
        // Populate handle (if provided), with the values ready for next iteration
        final String handleStr = getIterateHandleAsString(processedFeatures.size(), offset, maxLimit, handle);
        return verticle.sendXyzResponse(
            routingContext,
            HttpResponseType.FEATURE_COLLECTION,
            new XyzFeatureCollection()
                .withFeatures(processedFeatures)
                .withNextPageToken(handleStr));
      } catch (NoCursor | NoSuchElementException emptyException) {
        logger.info("No data found in ResultCursor, returning empty collection");
        return verticle.sendXyzResponse(
            routingContext, HttpResponseType.FEATURE_COLLECTION, emptyFeatureCollection());
      }
    }
  }

  private static String getIterateHandleAsString(
      long featuresFound, long crtOffset, long maxLimit, final @Nullable IterateHandle handle) {
    // nothing to populate if handle is not provided OR if we don't have more features to iterate
    if (handle == null || featuresFound < maxLimit) {
      return null;
    }
    handle.setOffset(crtOffset + featuresFound); // set offset for next iteration
    handle.setLimit(maxLimit);
    return handle.base64EncodedSerializedJson();
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformWriteResultToXyzCollectionResponse(
      final @Nullable Result wrResult,
      final @NotNull Class<R> type,
      final boolean isDeleteOperation,
      final @Nullable FeaturePostProcessor<R> featurePostProcessor) {
    final XyzResponse validatedErrorResponse = validateErrorResult(wrResult);
    if (validatedErrorResponse != null) {
      return validatedErrorResponse;
    } else {
      try {
        final Map<EExecutedOp, List<R>> featureMap =
            postProcessedFeaturesByOp(wrResult, type, featurePostProcessor);
        final List<R> insertedFeatures = featureMap.get(EExecutedOp.CREATED);
        final List<R> updatedFeatures = featureMap.get(EExecutedOp.UPDATED);
        final List<R> deletedFeatures = featureMap.get(EExecutedOp.DELETED);
        return verticle.sendXyzResponse(
            routingContext,
            HttpResponseType.FEATURE_COLLECTION,
            new XyzFeatureCollection()
                .withInsertedFeatures(insertedFeatures)
                .withUpdatedFeatures(updatedFeatures)
                .withDeletedFeatures(deletedFeatures)
                .withViolations(postProcessedViolations(wrResult, featurePostProcessor)));
      } catch (NoCursor | NoSuchElementException emptyException) {
        if (isDeleteOperation) {
          logger.info("No data found in ResultCursor, returning empty collection");
          return verticle.sendXyzResponse(
              routingContext, HttpResponseType.FEATURE_COLLECTION, emptyFeatureCollection());
        }
        return verticle.sendErrorResponse(
            routingContext, XyzError.EXCEPTION, "Unexpected empty result from ResultCursor");
      }
    }
  }

  /**
   * Extracts violations from a {@link ContextXyzFeatureResult} and applies the given post-processor to each one.
   * Returns {@code null} if the result carries no violations.
   */
  @SuppressWarnings("unchecked")
  private static <R extends XyzFeature> @Nullable List<XyzFeature> postProcessedViolations(
      @Nullable Result result, @Nullable FeaturePostProcessor<R> postProcessor) {
    if (!(result instanceof ContextXyzFeatureResult cr)) {
      return null;
    }
    final List<XyzFeature> rawViolations = cr.getViolations();
    if (rawViolations == null || postProcessor == null) {
      return rawViolations;
    }
    final List<XyzFeature> violations = new ArrayList<>();
    for (XyzFeature violation : rawViolations) {
      violations.add(postProcessor.postProcess((R) violation));
    }
    return violations;
  }

  /**
   * Helper method to fetch features from given Result and return a map of multiple lists grouped by {@link EExecutedOp} of features with
   * type T. Returned lists are limited with respect to supplied `limit` parameter.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param <R>         type of feature
   * @return a map grouping the lists of features extracted from ReadResult
   */
  private static <R extends XyzFeature> Map<EExecutedOp, List<R>> postProcessedFeaturesByOp(
      Result result, Class<R> featureType, FeaturePostProcessor<R> postProcessor) throws NoCursor {
    try (ForwardCursor<XyzFeature, XyzFeatureCodec> resultCursor = result.getXyzFeatureCursor()) {
      final List<R> insertedFeatures = new ArrayList<>();
      final List<R> updatedFeatures = new ArrayList<>();
      final List<R> deletedFeatures = new ArrayList<>();
      while (resultCursor.hasNext()) {
        if (!resultCursor.next()) {
          throw new RuntimeException("Unexpected invalid result");
        }
        R feature = featureType.cast(resultCursor.getFeature());
        if (postProcessor != null) {
          feature = postProcessor.postProcess(feature);
        }
        if (resultCursor.getOp().equals(EExecutedOp.CREATED)) {
          insertedFeatures.add(feature);
        } else if (resultCursor.getOp().equals(EExecutedOp.UPDATED)) {
          updatedFeatures.add(feature);
        } else if (resultCursor.getOp().equals(EExecutedOp.DELETED)) {
          deletedFeatures.add(feature);
        }
      }
      final Map<EExecutedOp, List<R>> features = new HashMap<>();
      features.put(EExecutedOp.CREATED, insertedFeatures);
      features.put(EExecutedOp.UPDATED, updatedFeatures);
      features.put(EExecutedOp.DELETED, deletedFeatures);
      return features;
    }
  }

  protected Result executeReadRequestFromSpaceStorage(ReadFeatures readRequest) {
    try (final IReadSession reader = naksha().getSpaceStorage().newReadSession(context(), false)) {
      return reader.execute(readRequest);
    }
  }

  protected Result executeWriteRequestFromSpaceStorage(WriteFeatures writeRequest) {
    try (final IWriteSession writer = naksha().getSpaceStorage().newWriteSession(context(), true)) {
      return writer.execute(writeRequest);
    }
  }

  XyzFeatureCollection emptyFeatureCollection() {
    return new XyzFeatureCollection().withFeatures(emptyList());
  }

  protected @Nullable XyzResponse validateErrorResultEmptyCollection(final @Nullable Result result) {
    if (result == null) {
      // return empty collection
      logger.warn("Unexpected null result, returning empty collection.");
      return verticle.sendXyzResponse(
          routingContext, HttpResponseType.FEATURE_COLLECTION, new XyzFeatureCollection());
    } else if (result instanceof ErrorResult er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error result {}", er);
      return verticle.sendErrorResponse(routingContext, er.reason, er.message);
    }
    return null;
  }

  protected @Nullable XyzResponse validateErrorResult(final @Nullable Result result) {
    if (result == null) {
      // return empty collection
      logger.error("Unexpected null result!");
      return verticle.sendErrorResponse(routingContext, XyzError.EXCEPTION, "Unexpected null result!");
    } else if (result instanceof ErrorResult er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error result {}", er);
      return verticle.sendErrorResponse(routingContext, er.reason, er.message);
    }
    return null;
  }

  protected <F> @NotNull F parseRequestBodyAs(final Class<F> type) throws JsonProcessingException {
    try (final Json json = Json.get()) {
      final String bodyJson = routingContext.body().asString();
      return json.reader(ViewDeserialize.User.class).forType(type).readValue(bodyJson);
    }
  }
}
