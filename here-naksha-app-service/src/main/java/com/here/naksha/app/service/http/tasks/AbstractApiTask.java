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
package com.here.naksha.app.service.http.tasks;

import static com.here.naksha.app.service.http.apis.ApiParams.DEF_ADMIN_FEATURE_LIMIT;
import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.FAIL_ON_NO_ELEMENTS;
import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeatureFromResult;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesGroupedByOp;
import static java.util.Collections.emptyList;

import com.here.naksha.app.service.http.HttpResponseType;
import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.lib.core.AbstractTask;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.storage.EExecutedOp;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.WriteFeatures;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import io.vertx.ext.web.RoutingContext;
import java.util.*;
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
    return transformResultToXyzFeatureResponse(rdResult, type, NOT_FOUND_ON_NO_ELEMENTS);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformWriteResultToXyzFeatureResponse(
      final @Nullable Result wrResult, final @NotNull Class<R> type) {
    return transformResultToXyzFeatureResponse(wrResult, type, FAIL_ON_NO_ELEMENTS);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformDeleteResultToXyzFeatureResponse(
      final @Nullable Result wrResult, final @NotNull Class<R> type) {
    return transformResultToXyzFeatureResponse(wrResult, type, NOT_FOUND_ON_NO_ELEMENTS);
  }

  private XyzResponse handleNoElements(NoElementsStrategy noElementsStrategy) {
    return verticle.sendErrorResponse(routingContext, noElementsStrategy.xyzError, noElementsStrategy.message);
  }

  private <R extends XyzFeature> @NotNull XyzResponse transformResultToXyzFeatureResponse(
      final @Nullable Result result,
      final @NotNull Class<R> type,
      final @NotNull NoElementsStrategy noElementsStrategy) {
    if (result == null) {
      logger.error("Unexpected null result!");
      return verticle.sendErrorResponse(routingContext, XyzError.EXCEPTION, "Unexpected null result!");
    } else if (result instanceof ErrorResult er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error result {}", er);
      return verticle.sendErrorResponse(routingContext, er.reason, er.message);
    } else {
      try {
        R feature = readFeatureFromResult(result, type);
        if (feature == null) {
          return verticle.sendErrorResponse(
              routingContext,
              XyzError.NOT_FOUND,
              "No feature found for id "
                  + result.getXyzFeatureCursor().getId());
        }
        if (Objects.equals(type, Storage.class)) {
          removePasswordFromFeature(feature);
        }
        final List<R> featureList = new ArrayList<>();
        featureList.add(feature);
        final XyzFeatureCollection featureResponse = new XyzFeatureCollection().withFeatures(featureList);
        return verticle.sendXyzResponse(routingContext, HttpResponseType.FEATURE, featureResponse);
      } catch (NoCursor | NoSuchElementException emptyException) {
        return handleNoElements(noElementsStrategy);
      }
    }
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzCollectionResponse(
      final @Nullable Result rdResult, final @NotNull Class<R> type) {
    return transformReadResultToXyzCollectionResponse(rdResult, type, DEF_ADMIN_FEATURE_LIMIT);
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformReadResultToXyzCollectionResponse(
      final @Nullable Result rdResult, final @NotNull Class<R> type, final long maxLimit) {
    if (rdResult == null) {
      // return empty collection
      logger.warn("Unexpected null result, returning empty collection.");
      return verticle.sendXyzResponse(
          routingContext, HttpResponseType.FEATURE_COLLECTION, new XyzFeatureCollection());
    } else if (rdResult instanceof ErrorResult er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error result {}", er);
      return verticle.sendErrorResponse(routingContext, er.reason, er.message);
    } else {
      try {
        List<R> features = readFeaturesFromResult(rdResult, type, maxLimit);
        if (Objects.equals(type, Storage.class)) {
          for (R feature : features) {
            removePasswordFromFeature(feature);
          }
        }
        return verticle.sendXyzResponse(
            routingContext,
            HttpResponseType.FEATURE_COLLECTION,
            new XyzFeatureCollection().withFeatures(features));
      } catch (NoCursor | NoSuchElementException emptyException) {
        logger.info("No data found in ResultCursor, returning empty collection");
        return verticle.sendXyzResponse(
            routingContext, HttpResponseType.FEATURE_COLLECTION, emptyFeatureCollection());
      }
    }
  }

  protected <R extends XyzFeature> @NotNull XyzResponse transformWriteResultToXyzCollectionResponse(
      final @Nullable Result wrResult, final @NotNull Class<R> type, final boolean isDeleteOperation) {
    if (wrResult == null) {
      // unexpected null response
      logger.error("Received null result!");
      return verticle.sendErrorResponse(routingContext, XyzError.EXCEPTION, "Unexpected null result!");
    } else if (wrResult instanceof ErrorResult er) {
      // In case of error, convert result to ErrorResponse
      logger.error("Received error result {}", er);
      return verticle.sendErrorResponse(routingContext, er.reason, er.message);
    } else {
      try {
        final Map<EExecutedOp, List<R>> featureMap = readFeaturesGroupedByOp(wrResult, type);
        final List<R> insertedFeatures = featureMap.get(EExecutedOp.CREATED);
        final List<R> updatedFeatures = featureMap.get(EExecutedOp.UPDATED);
        final List<R> deletedFeatures = featureMap.get(EExecutedOp.DELETED);
        if (Objects.equals(type, Storage.class)) {
          for (R feature : insertedFeatures) {
            removePasswordFromFeature(feature);
          }
          for (R feature : updatedFeatures) {
            removePasswordFromFeature(feature);
          }
          for (R feature : deletedFeatures) {
            removePasswordFromFeature(feature);
          }
        }
        return verticle.sendXyzResponse(
            routingContext,
            HttpResponseType.FEATURE_COLLECTION,
            new XyzFeatureCollection()
                .withInsertedFeatures(insertedFeatures)
                .withUpdatedFeatures(updatedFeatures)
                .withDeletedFeatures(deletedFeatures));
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

  private XyzFeatureCollection emptyFeatureCollection() {
    return new XyzFeatureCollection().withFeatures(emptyList());
  }

  private Map<String, Object> removePasswordFromProps(Map<String, Object> propertiesAsMap) {
    for (Iterator<Map.Entry<String, Object>> it = propertiesAsMap.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Object> entry = it.next();
      if (Objects.equals(entry.getKey(), "password")) {
        it.remove();
      } else if (entry.getValue() instanceof Map) {
        // recursive call to the nested json property
        removePasswordFromProps((Map<String, Object>) entry.getValue());
      } else if (entry.getValue() instanceof ArrayList array) {
        // recursive call to the nested array json
        for (Object arrayEntry : array) {
          removePasswordFromProps((Map<String, Object>) arrayEntry);
        }
        entry.setValue(array);
      }
    }
    return propertiesAsMap;
  }

  private <R extends XyzFeature> void removePasswordFromFeature(final @NotNull R feature) {
    Map<String, Object> propertiesAsMap =
        removePasswordFromProps(feature.getProperties().asMap());
    feature.setProperties(JsonSerializable.fromMap(propertiesAsMap, XyzProperties.class));
  }
}
