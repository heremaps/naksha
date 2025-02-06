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

import static com.here.naksha.app.service.http.apis.ApiParams.extractParamAsStringList;
import static com.here.naksha.app.service.http.apis.ApiParams.queryParamsFromRequest;
import static com.here.naksha.app.service.http.apis.ApiParams.validateFeatureId;
import static com.here.naksha.common.http.apis.ApiParamsConst.ADD_TAGS;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;
import static com.here.naksha.common.http.apis.ApiParamsConst.REMOVE_TAGS;
import static com.here.naksha.common.http.apis.ApiParamsConst.SPACE_ID;
import static com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper.proxyWrapperOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.app.service.http.HttpResponseType;
import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.apis.ApiParams;
import com.here.naksha.app.service.models.FeatureCollectionRequest;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.lambdas.P;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import naksha.diff.Difference;
import naksha.diff.DifferenceCalculator;
import naksha.diff.DifferenceFilter;
import naksha.diff.Patcher;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.WriteRequest;
import naksha.model.util.RequestHelper;
import naksha.model.util.ResultHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  protected void init() {
  }

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
        case CREATE_FEATURES -> executeCreateOrPatchFeatures();
        case UPSERT_FEATURES -> executeUpsertFeatures();
        case UPDATE_BY_ID -> executeUpdateFeature();
        case DELETE_FEATURES -> executeDeleteFeatures();
        case DELETE_BY_ID -> executeDeleteFeature();
        case PATCH_BY_ID -> executePatchFeatureById();
        default -> executeUnsupported();
      };
    } catch (NakshaException ex) {
      logger.warn("Known exception while processing request. ", ex);
      return verticle.sendErrorResponse(routingContext, ex.error);
    } catch (Exception ex) {
      logger.error("Unexpected error while processing request. ", ex);
      return verticle.sendErrorResponse(
          routingContext, NakshaError.EXCEPTION, "Internal error : " + ex.getMessage());
    }
  }

  private @NotNull XyzResponse executeCreateOrPatchFeatures() throws Exception {
    // Deserialize input request
    final FeatureCollectionRequest collectionRequest = parseRequestBodyAs(FeatureCollectionRequest.class);
    final NakshaFeatureList features = collectionRequest.getFeatures();
    if (features.isEmpty()) {
      return verticle.sendErrorResponse(routingContext, NakshaError.ILLEGAL_ARGUMENT, "Can't create empty features");
    }

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
    final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);

    return attemptFeaturesPatching(spaceId, features, HttpResponseType.FEATURE_COLLECTION, addTags, removeTags, 0);
  }

  private @NotNull XyzResponse executeUpsertFeatures() throws Exception {
    // Deserialize input request
    final FeatureCollectionRequest collectionRequest = parseRequestBodyAs(FeatureCollectionRequest.class);
    final NakshaFeatureList features = collectionRequest.getFeatures();
    if (features.isEmpty()) {
      return verticle.sendErrorResponse(routingContext, NakshaError.ILLEGAL_ARGUMENT, "Can't update empty features");
    }

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
    final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);

    // as applicable, modify features based on parameters supplied
    for (final NakshaFeature feature : features) {
      addTagsToFeature(feature, addTags);
      removeTagsFromFeature(feature, removeTags);
    }
    final WriteRequest wrRequest = RequestHelper.upsertFeaturesRequest(spaceId, features);

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformWriteResultToXyzCollectionResponse(response, NakshaFeature.class, false);
  }

  private @NotNull XyzResponse executeUpdateFeature() throws Exception {
    // Deserialize input request
    final NakshaFeature feature = parseRequestBodyAs(NakshaFeature.class);

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

    final WriteRequest wrRequest = RequestHelper.updateFeatureRequest(spaceId, feature);

    // Forward request to NH Space Storage writer instance
    Result wrResult = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformResponseToXyzFeatureResponse(wrResult, NakshaFeature.class, NoElementsStrategy.FAIL_ON_NO_ELEMENTS);

  }

  private @NotNull XyzResponse executeDeleteFeatures() {
    // Deserialize input request
    final QueryParameterList queryParameters = queryParamsFromRequest(routingContext);
    final List<String> features = extractParamAsStringList(queryParameters, FEATURE_IDS);
    if (features == null || features.isEmpty()) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, "Missing feature id parameter");
    }

    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    final WriteRequest wrRequest = RequestHelper.deleteFeaturesByIdsRequest(spaceId, features);

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformWriteResultToXyzCollectionResponse(response, NakshaFeature.class, true);
  }

  private @NotNull XyzResponse executeDeleteFeature() {
    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final String featureId = ApiParams.extractMandatoryPathParam(routingContext, FEATURE_ID);

    final WriteRequest wrRequest = RequestHelper.deleteFeatureByIdRequest(spaceId, featureId);

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformResponseToXyzFeatureResponse(response, NakshaFeature.class, NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS);
  }

  private static final int MAX_RETRY_ATTEMPT = 5;

  private @NotNull XyzResponse executePatchFeatureById() throws JsonProcessingException {

    final NakshaFeature featureFromRequest = parseRequestBodyAs(NakshaFeature.class);

    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    final List<String> addTags = extractParamAsStringList(queryParams, ADD_TAGS);
    final List<String> removeTags = extractParamAsStringList(queryParams, REMOVE_TAGS);

    // Validate parameters
    validateFeatureId(routingContext, featureFromRequest.getId());

    final List<NakshaFeature> featuresFromRequest = new ArrayList<>();
    featuresFromRequest.add(featureFromRequest);
    return attemptFeaturesPatching(spaceId, featuresFromRequest, HttpResponseType.FEATURE, addTags, removeTags, 0);
  }

  private XyzResponse attemptFeaturesPatching(
      @NotNull String spaceId,
      @NotNull List<NakshaFeature> featuresFromRequest,
      @NotNull HttpResponseType responseType,
      @Nullable List<String> addTags,
      @Nullable List<String> removeTags,
      int retry) {
    // Patched feature list is to ensure the order of input features is retained
    final List<NakshaFeature> patchedFeatures;
    final List<String> featureIds = new ArrayList<>();
    for (NakshaFeature feature : featuresFromRequest) {
      featureIds.add(feature.getId());
    }
    // Extract the version of features in storage
    final ReadFeatures rdRequest = proxyWrapperOf(RequestHelper.readFeaturesByIdsRequest(spaceId, featureIds))
        .withReadRequestType(ReadFeaturesProxyWrapper.ReadRequestType.GET_BY_IDS)
        .withQueryParameters(Map.of(FEATURE_IDS, featureIds));

    Response response = executeReadRequestFromSpaceStorage(rdRequest);
    List<NakshaFeature> featuresToPatchFromStorage = new ArrayList<>();
    if(response instanceof SuccessResponse successResponse){
      featuresToPatchFromStorage = ResultHelper.extractResponseItems(successResponse, NakshaFeature.class);
    } else if(response instanceof ErrorResponse errorResponse){
      return returnError(
          errorResponse.getError(),
          "Error encountered while reading features from storage: {}",
          featureIds);
    } else {
      return returnError(
          NakshaError.EXCEPTION,
          "Unexpected response while reading features from storage: " + response,
          "Unexpected null result while reading features from storage: {}",
          featureIds);
    }
    if(featuresToPatchFromStorage.isEmpty()){
      if(responseType == HttpResponseType.FEATURE){
        return returnError(
            NakshaError.NOT_FOUND,
            "Feature does not exist.",
            "Unexpected null result while reading current versions in storage of targeted features for PATCH. The feature does not exist.");
      } else if (!responseType.equals(HttpResponseType.FEATURE_COLLECTION)) {
        // This function was then misused somewhere. FIND AND FIX IT!!
        return returnError(
            NakshaError.EXCEPTION,
            "Internal server error.",
            "Unsupported HttpResponseType was called: {}",
            responseType);
    }
      // Else none of the features exists in storage, will create them later
    }
    // Attempt patching, keeping the order of the features from the request
    patchedFeatures =
        performInMemoryPatching(featuresFromRequest, featuresToPatchFromStorage, addTags, removeTags);

    final WriteRequest wrRequest = RequestHelper.upsertFeaturesRequest(spaceId, patchedFeatures);
    // Forward request to NH Space Storage writer instance
    try (final IWriteSession writer = naksha().getSpaceStorage().newWriteSession(SessionOptions.from(context(), true))) {
      final Response wrResponse = writer.execute(wrRequest);
      if (wrResponse == null) {
        // unexpected null response
        writer.rollback();
        writer.close();
        return returnError(
            NakshaError.EXCEPTION,
            "Unexpected null result.",
            "Received null result after writing patched features, rolled back.");
      } else if (wrResponse instanceof ErrorResponse er) {
        writer.rollback();
        writer.close();

        // TODO (Jakub): start over here: mismatching UUID logic

        try (ForwardCursor<XyzFeature, XyzFeatureCodec> resultCursor = er.getXyzFeatureCursor()) {
          if (!resultCursor.hasNext()) {
            throw new NoSuchElementException("Error Result Cursor is empty");
          }
          while (resultCursor.hasNext()) {
            if (!resultCursor.next()) {
              throw new RuntimeException("Unexpected invalid error result");
            }
            // Check if there is an error that is not about mismatching UUID
            if (EExecutedOp.ERROR.equals(resultCursor.getOp())) {
              if (!XyzError.CONFLICT.equals(Objects.requireNonNull(resultCursor.getError()).err)) {
                // Other types of error, will not retry
                return returnError(
                    resultCursor.getError().err,
                    resultCursor.getError().msg,
                    "Received error result {}",
                    resultCursor.getError());
              }
              // Else it was because of UUID mismatched
              final String featureIdFromErr = resultCursor.getId();
              // Find the requested change for the corresponding feature with that ID
              for (XyzFeature requestedChange : featuresFromRequest) {
                if (featureIdFromErr.equals(requestedChange.getId())) {
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
          }
          // Else the feature was modified concurrently within Naksha
          if (retry >= MAX_RETRY_ATTEMPT) {
            return returnError(
                XyzError.EXCEPTION,
                "Max retry attempt for PATCH REST API reached, too many concurrent modification, error: "
                + er.message,
                "Max retry attempt for PATCH REST API reached, too many concurrent modification, error: {}",
                er.message);
          }
          // Attempt retry
          resultCursor.close();
          return attemptFeaturesPatching(
              spaceId, featuresFromRequest, responseType, addTags, removeTags, retry + 1);
        } catch (NoCursor e) {
          return returnError(
              XyzError.EXCEPTION,
              "Unexpected response when trying to persist patched features.",
              "No cursor when analyzing error result, while attempting to write patched features into storage.");
        }
      } else {
        if (responseType.equals(HttpResponseType.FEATURE)) {
          return transformResponseToXyzFeatureResponse(wrResponse, NakshaFeature.class, NoElementsStrategy.FAIL_ON_NO_ELEMENTS);
        }
        return transformResponseToXyzFeatureResponse(wrResponse, NakshaFeature.class, false);
      }
    }
  }

  /**
   * Return a list of patched XyzFeature, including the ones not yet existing, ready for upsert
   */
  private List<NakshaFeature> performInMemoryPatching(
      @NotNull List<NakshaFeature> featuresFromRequest,
      List<NakshaFeature> featuresToPatchFromStorage,
      @Nullable List<String> addTags,
      @Nullable List<String> removeTags) {
    final List<NakshaFeature> patchedFeatureList = new ArrayList<>();
    for (final NakshaFeature inputFeature : featuresFromRequest) {
      // we take input feature as default
      NakshaFeature featureToPatch = inputFeature;
      // check if input feature matches with any of the existing features in storage
      if (inputFeature.getId() != null) {
        for (NakshaFeature storageFeature : featuresToPatchFromStorage) {
          if (inputFeature.getId().equals(storageFeature.getId())) {
            // we found matching feature in storage, so we take patched version of the feature
            final Difference difference = DifferenceCalculator.calculateDifference(storageFeature, inputFeature);
            DifferenceFilter.removeAllRemoveOp(difference);
            featureToPatch = Patcher.patch(storageFeature, difference);
            break;
          }
        }
      }
      // We now have featureToPatch which needs to be modified (if needed) and to be added to the list
      addTagsToFeature(featureToPatch, addTags);
      removeTagsFromFeature(featureToPatch, removeTags);
      patchedFeatureList.add(featureToPatch);
    }
    return patchedFeatureList;
  }

  private XyzResponse returnError(String errorCode, String errorMsg, String internalLogMsg, Object... logArgs) {
    return returnError(new NakshaError(errorCode, errorMsg), internalLogMsg, logArgs);
  }

  private XyzResponse returnError(NakshaError nakshaError, String internalLogMsg, Object... logArgs) {
    logger.error(internalLogMsg, logArgs);
    return verticle.sendErrorResponse(routingContext, nakshaError);
  }

  private void addTagsToFeature(NakshaFeature feature, List<String> addTags) {
    if (addTags != null) {
      feature.getProperties().getXyz().addTags(addTags, true);
    }
  }

  private void removeTagsFromFeature(NakshaFeature feature, List<String> removeTags) {
    if (removeTags != null) {
      feature.getProperties().getXyz().removeTags(removeTags, true);
    }
  }
}
