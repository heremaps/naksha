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

import static com.here.naksha.app.service.http.apis.ApiParams.*;
import static com.here.naksha.lib.core.util.diff.PatcherUtils.removeAllRemoveOp;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.app.service.http.HttpResponseType;
import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.apis.ApiParams;
import com.here.naksha.app.service.models.FeatureCollectionRequest;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.core.util.diff.Difference;
import com.here.naksha.lib.core.util.diff.Patcher;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.lib.core.view.ViewDeserialize;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteFeatureApiTask<T extends XyzResponse> extends AbstractApiTask<XyzResponse> {

  private static final Logger logger = LoggerFactory.getLogger(WriteFeatureApiTask.class);
  private final @NotNull WriteFeatureApiReqType reqType;

  public enum WriteFeatureApiReqType {
    CREATE_FEATURES,
    UPSERT_FEATURES,
    UPDATE_BY_ID,
    DELETE_FEATURES,
    DELETE_BY_ID,
    PATCH_BY_ID
  }

  public WriteFeatureApiTask(
      final @NotNull WriteFeatureApiReqType reqType,
      final @NotNull NakshaHttpVerticle verticle,
      final @NotNull INaksha nakshaHub,
      final @NotNull RoutingContext routingContext,
      final @NotNull NakshaContext nakshaContext) {
    super(verticle, nakshaHub, routingContext, nakshaContext);
    this.reqType = reqType;
  }

  /**
   * Initializes this task.
   */
  @Override
  protected void init() {}

  /**
   * Execute this task.
   *
   * @return the response.
   */
  @Override
  protected @NotNull XyzResponse execute() {
    logger.info("Received Http request {}", this.reqType);
    // Custom execute logic to process input API request based on reqType
    try {
      return switch (this.reqType) {
          // TODO : POST API needs to act as UPSERT for UI wiring due to backward compatibility.
          //  It may need to be readjusted, once we better understand difference
          //  (if there is anything other than PATCH, which is already known)
        case CREATE_FEATURES -> executeUpsertFeatures();
        case UPSERT_FEATURES -> executeUpsertFeatures();
        case UPDATE_BY_ID -> executeUpdateFeature();
        case DELETE_FEATURES -> executeDeleteFeatures();
        case DELETE_BY_ID -> executeDeleteFeature();
        case PATCH_BY_ID -> executePatchFeatureById();
        default -> executeUnsupported();
      };
    } catch (XyzErrorException ex) {
      return verticle.sendErrorResponse(routingContext, ex.xyzError, ex.getMessage());
    } catch (Exception ex) {
      // unexpected exception
      logger.error("Exception processing Http request. ", ex);
      return verticle.sendErrorResponse(
          routingContext, XyzError.EXCEPTION, "Internal error : " + ex.getMessage());
    }
  }

  private @NotNull XyzResponse executeCreateFeatures() throws Exception {
    // Deserialize input request
    final FeatureCollectionRequest collectionRequest = featuresFromRequestBody();
    final List<XyzFeature> features = (List<XyzFeature>) collectionRequest.getFeatures();
    if (features.isEmpty()) {
      return verticle.sendErrorResponse(routingContext, XyzError.ILLEGAL_ARGUMENT, "Can't create empty features");
    }

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    final String prefixId = extractParamAsString(queryParams, PREFIX_ID);
    final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
    final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);

    // as applicable, modify features based on parameters supplied
    for (final XyzFeature feature : features) {
      feature.setIdPrefix(prefixId);
      addTagsToFeature(feature, addTags);
      removeTagsFromFeature(feature, removeTags);
    }

    final WriteXyzFeatures wrRequest = RequestHelper.createFeaturesRequest(spaceId, features);

    // Forward request to NH Space Storage writer instance
    try (Result wrResult = executeWriteRequestFromSpaceStorage(wrRequest)) {
      // transform WriteResult to Http FeatureCollection response
      return transformWriteResultToXyzCollectionResponse(wrResult, XyzFeature.class, false);
    }
  }

  private @NotNull XyzResponse executeUpsertFeatures() throws Exception {
    // Deserialize input request
    final FeatureCollectionRequest collectionRequest = featuresFromRequestBody();
    final List<XyzFeature> features = (List<XyzFeature>) collectionRequest.getFeatures();
    if (features.isEmpty()) {
      return verticle.sendErrorResponse(routingContext, XyzError.ILLEGAL_ARGUMENT, "Can't update empty features");
    }

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
    final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);

    // as applicable, modify features based on parameters supplied
    for (final XyzFeature feature : features) {
      addTagsToFeature(feature, addTags);
      removeTagsFromFeature(feature, removeTags);
    }
    final WriteXyzFeatures wrRequest = RequestHelper.upsertFeaturesRequest(spaceId, features);

    // Forward request to NH Space Storage writer instance
    try (Result wrResult = executeWriteRequestFromSpaceStorage(wrRequest)) {
      // transform WriteResult to Http FeatureCollection response
      return transformWriteResultToXyzCollectionResponse(wrResult, XyzFeature.class, false);
    }
  }

  private @NotNull XyzResponse executeUpdateFeature() throws Exception {
    // Deserialize input request
    final XyzFeature feature = singleFeatureFromRequestBody();

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
    final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);

    // Validate parameters
    validateFeatureId(routingContext, feature.getId());

    // as applicable, modify features based on parameters supplied
    addTagsToFeature(feature, addTags);
    removeTagsFromFeature(feature, removeTags);

    final WriteXyzFeatures wrRequest = RequestHelper.updateFeatureRequest(spaceId, feature);

    // Forward request to NH Space Storage writer instance
    try (Result wrResult = executeWriteRequestFromSpaceStorage(wrRequest)) {
      // transform WriteResult to Http FeatureCollection response
      return transformWriteResultToXyzFeatureResponse(wrResult, XyzFeature.class);
    }
  }

  private @NotNull XyzResponse executeDeleteFeatures() {
    // Deserialize input request
    final QueryParameterList queryParameters = queryParamsFromRequest(routingContext);
    final List<String> features = extractParamAsStringList(queryParameters, FEATURE_IDS);
    if (features == null || features.isEmpty()) {
      return verticle.sendErrorResponse(
          routingContext, XyzError.ILLEGAL_ARGUMENT, "Missing feature id parameter");
    }

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    final WriteXyzFeatures wrRequest = RequestHelper.deleteFeaturesByIdsRequest(spaceId, features);

    // Forward request to NH Space Storage writer instance
    try (Result wrResult = executeWriteRequestFromSpaceStorage(wrRequest)) {
      // transform WriteResult to Http FeatureCollection response
      return transformWriteResultToXyzCollectionResponse(wrResult, XyzFeature.class, true);
    }
  }

  private @NotNull XyzResponse executeDeleteFeature() {
    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final String featureId = ApiParams.extractMandatoryPathParam(routingContext, FEATURE_ID);

    final WriteXyzFeatures wrRequest = RequestHelper.deleteFeatureByIdRequest(spaceId, featureId);

    // Forward request to NH Space Storage writer instance
    try (Result wrResult = executeWriteRequestFromSpaceStorage(wrRequest)) {
      // transform WriteResult to Http FeatureCollection response
      return transformDeleteResultToXyzFeatureResponse(wrResult, XyzFeature.class);
    }
  }

  private static final int MAX_RETRY_ATTEMPT = 5;

  private @NotNull XyzResponse executePatchFeatureById() throws JsonProcessingException {

    final XyzFeature featureFromRequest = singleFeatureFromRequestBody();

    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    // Validate parameters
    validateFeatureId(routingContext, featureFromRequest.getId());

    final List<XyzFeature> featuresFromRequest = new ArrayList<>();
    featuresFromRequest.add(featureFromRequest);
    return attemptFeaturesPatching(spaceId, featuresFromRequest, HttpResponseType.FEATURE, 0);
  }

  private XyzResponse attemptFeaturesPatching(
      @NotNull String spaceId,
      @NotNull List<XyzFeature> featuresFromRequest,
      @NotNull HttpResponseType responseType,
      int retry) {
    // Patched feature list is to ensure the order of input features is retained
    final List<XyzFeature> patchedFeature;
    List<XyzFeature> featuresToPatchFromStorage = new ArrayList<>();
    final List<String> featureIds = new ArrayList<>();
    for (XyzFeature feature : featuresFromRequest) {
      if (feature.getId() != null) {
        featureIds.add(feature.getId());
      }
    }
    // Extract the version of features in storage
    final ReadFeatures rdRequest = RequestHelper.readFeaturesByIdsRequest(spaceId, featureIds);
    try (Result result = executeReadRequestFromSpaceStorage(rdRequest)) {
      if (result == null) {
        logger.error("Unexpected null result while reading features from storage: " + featureIds);
        return verticle.sendErrorResponse(
            routingContext,
            XyzError.EXCEPTION,
            "Unexpected null result while reading features from storage");
      } else if (result instanceof ErrorResult er) {
        // In case of error, convert result to ErrorResponse
        logger.error("Received error result while reading features in storage: {}", er);
        return verticle.sendErrorResponse(routingContext, er.reason, er.message);
      }
      try {
        featuresToPatchFromStorage = readFeaturesFromResult(result, XyzFeature.class, DEF_FEATURE_LIMIT);
      } catch (NoCursor | NoSuchElementException emptyException) {
        if (responseType.equals(HttpResponseType.FEATURE)) {
          // TODO strategy pattern?
          // If this is patching only 1 feature (PATCH by ID), return not found
          logger.error(
              "Unexpected null result while reading current versions in storage of targeted features for PATCH. The feature does not exist.");
          return verticle.sendErrorResponse(routingContext, XyzError.NOT_FOUND, "Feature does not exist.");
        } else if (!responseType.equals(HttpResponseType.FEATURE_COLLECTION)) {
          // This function was then misused somewhere. FIND AND FIX IT!!
          logger.error("Unsupported HttpResponseType was called: " + responseType);
          return verticle.sendErrorResponse(routingContext, XyzError.EXCEPTION, "Internal server error.");
        }
        // Else none of the features exists in storage, will create them later
      }
      // As applicable, modify features based on parameters supplied
      final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
      final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
      final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);
      for (XyzFeature featureFromRequest : featuresFromRequest) {
        addTagsToFeature(featureFromRequest, addTags);
        removeTagsFromFeature(featureFromRequest, removeTags);
      }
      // Attempt patching, keeping the order of the features from the request
      patchedFeature = performInMemoryPatching(featuresFromRequest, featuresToPatchFromStorage);
    }
    final WriteXyzFeatures wrRequest = RequestHelper.upsertFeaturesRequest(spaceId, patchedFeature);
    // Forward request to NH Space Storage writer instance
    try (final IWriteSession writer = naksha().getSpaceStorage().newWriteSession(context(), true)) {
      final Result wrResult = writer.execute(wrRequest);
      if (wrResult == null) {
        // unexpected null response
        logger.error("Received null result!");
        writer.rollback(true);
        writer.close();
        return verticle.sendErrorResponse(routingContext, XyzError.EXCEPTION, "Unexpected null result!");
      } else if (wrResult instanceof ErrorResult er) {
        writer.rollback(true);
        writer.close();
        try (ForwardCursor<XyzFeature, XyzFeatureCodec> resultCursor = er.getXyzFeatureCursor()) {
          if (!resultCursor.hasNext()) {
            throw new NoSuchElementException("Error Result Cursor is empty");
          }
          while (resultCursor.hasNext()) {
            if (!resultCursor.next()) {
              throw new RuntimeException("Unexpected invalid error result");
            }
            if (!er.reason.equals(XyzError.CONFLICT)) {
              // Other types of error, will not retry
              logger.error("Received error result {}", resultCursor.getError());
              return verticle.sendErrorResponse(
                  routingContext, Objects.requireNonNull(resultCursor.getError()).err, resultCursor.getError().msg);
            }
            // Else it was because of UUID mismatched
            final String featureIdFromErr = resultCursor.getId();
            // Find the requested change for the corresponding feature with that ID
            for (XyzFeature requestedChange : featuresFromRequest) {
              if (requestedChange.getId().equals(featureIdFromErr)) {
                // If UUID input by user, will not retry, return conflict
                if (requestedChange
                        .getProperties()
                        .getXyzNamespace()
                        .getUuid()
                    != null) {
                  return verticle.sendErrorResponse(
                      routingContext,
                      XyzError.CONFLICT,
                      "Error updating feature '" + featureIdFromErr + "', wrong UUID.");
                }
              }
            }
          }
          // Else the feature was modified concurrently within Naksha
          if (retry >= MAX_RETRY_ATTEMPT) {
            logger.error(
                "Max retry attempt for PATCH REST API reached, too many concurrent modification, error: {}",
                er.message);
            return verticle.sendErrorResponse(
                routingContext,
                XyzError.EXCEPTION,
                "Max retry attempt for PATCH REST API reached, too many concurrent modification, error: "
                    + er.message);
          }
          // Attempt retry
          return attemptFeaturesPatching(spaceId, featuresFromRequest, responseType, retry + 1);
        } catch (NoCursor e) {
          logger.error(
              "No cursor when analyzing error result, while attempting to write patched features into storage.");
          return verticle.sendErrorResponse(
              routingContext,
              XyzError.EXCEPTION,
              "Unexpected response when trying to persist patched features.");
        }
      } else {
        if (responseType.equals(HttpResponseType.FEATURE)) {
          return transformWriteResultToXyzFeatureResponse(wrResult, XyzFeature.class);
        }
        return transformWriteResultToXyzCollectionResponse(wrResult, XyzFeature.class, false);
      }
    }
  }

  /**
   * Return a list of patched XyzFeature, including the ones not yet existing, ready for upsert
   */
  private List<XyzFeature> performInMemoryPatching(
      @NotNull List<XyzFeature> featuresFromRequest, List<XyzFeature> featuresToPatchFromStorage) {
    final List<XyzFeature> patchedFeature = new ArrayList<>();
    for (XyzFeature requestedChange : featuresFromRequest) {
      if (requestedChange.getId() == null) {
        // This requested feature has no ID, hence does not exist, create it
        patchedFeature.add(requestedChange);
        continue;
      }
      boolean willPatch = false;
      for (XyzFeature featureToPatch : featuresToPatchFromStorage) {
        if (requestedChange.getId().equals(featureToPatch.getId())) {
          final Difference difference = Patcher.getDifference(featureToPatch, requestedChange);
          final Difference diffNoRemoveOp = removeAllRemoveOp(difference);
          patchedFeature.add(Patcher.patch(featureToPatch, diffNoRemoveOp));
          willPatch = true;
          break;
        }
      }
      if (!willPatch) {
        // This requested feature with specified ID does not exist, create it
        patchedFeature.add(requestedChange);
      }
    }
    return patchedFeature;
  }

  private @NotNull FeatureCollectionRequest featuresFromRequestBody() throws JsonProcessingException {
    try (final Json json = Json.get()) {
      final String bodyJson = routingContext.body().asString();
      return json.reader(ViewDeserialize.User.class)
          .forType(FeatureCollectionRequest.class)
          .readValue(bodyJson);
    }
  }

  private @NotNull XyzFeature singleFeatureFromRequestBody() throws JsonProcessingException {
    try (final Json json = Json.get()) {
      final String bodyJson = routingContext.body().asString();
      return json.reader(ViewDeserialize.User.class)
          .forType(XyzFeature.class)
          .readValue(bodyJson);
    }
  }

  private void addTagsToFeature(XyzFeature feature, List<String> addTags) {
    if (addTags != null) {
      feature.getProperties().getXyzNamespace().addTags(addTags, true);
    }
  }

  private void removeTagsFromFeature(XyzFeature feature, List<String> removeTags) {
    if (removeTags != null) {
      feature.getProperties().getXyzNamespace().removeTags(removeTags, true);
    }
  }
}
