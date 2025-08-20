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
import static naksha.model.util.RequestHelper.atomicUpdateFeatureRequest;
import static naksha.model.util.RequestHelper.nonAtomicUpdateFeatureRequest;

import com.here.naksha.app.service.http.HttpResponseType;
import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.apis.ApiParams;
import com.here.naksha.app.service.models.FeatureCollectionRequest;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import naksha.base.MapProxy;
import naksha.diff.Difference;
import naksha.diff.DifferenceCalculator;
import naksha.diff.DifferenceFilter;
import naksha.diff.Patcher;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.util.RequestHelper;
import naksha.model.util.ResultHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteFeatureApiTask extends AbstractApiTask<XyzResponse> {

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
      final @NotNull NakshaContext nakshaContext
  ) {
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
      return verticle.sendErrorResponse(routingContext, ex.getError());
    } catch (Exception ex) {
      logger.error("Unexpected error while processing request. ", ex);
      return verticle.sendErrorResponse(
          routingContext, NakshaError.EXCEPTION, "Internal error : " + ex.getMessage());
    }
  }

  private @NotNull XyzResponse executeCreateOrPatchFeatures() {
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

  private @NotNull XyzResponse executeUpsertFeatures() {
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

    // TODO: alweber: Why do we send `naksha.model.request.WriteRequest`'s through the pipeline?
    //       Naksha-Hub should define its own events (which may still extend the base Request).
    //       The reason to internally NOT reuse the `naksha.model.request.WriteRequest` is simply
    //       that a space has nothing to do with a storage. A space is a pipeline of handlers,
    //       and the input into these handlers is a list of features with an instruction what to
    //       do with them. This instruction could be any virtual one, like `moderate`, `verify`, ...
    //       Clearly, it could be as well `create`, `update`, `upsert`, `patch`, `delete` or `purge`.
    //       However, these are logical, while a `naksha.model.request.WriteRequest` has a concrete
    //       `naksha.model.request.Write` instructions with precise requirement where to mutate
    //       the feature, so it requires to know the storage, the map, and the collection where the
    //       feature is located.
    //       For all other handlers and the REST side, we do not really need to know this, we do not
    //       even need to know if the feature is going to end up within a storage. We should actually
    //       move the transformation from the logical instruction into a concrete storage write into
    //       the storage handler.
    //       IMHO, we need to change this to use logical events, like `ModifyFeatureRequest`. Within
    //       that request, there should be no mentioning where the feature actually ends up, do not
    //       mention something about storage, map, or collection. When a patch is needed, the
    //       corresponding patch-handler will first issue a logical `ReadFeaturesRequest` and send it
    //       through the pipeline, hoping that any other handler can resolve it and return a storage
    //       `SuccessResponse`, with the requested features added. Beware, there can be multiple storages
    //       attached to the space! After it has the result of the `ReadFeaturesRequest` it performs the
    //       patch, three-way-merge, and all other operations supported by the handler, and eventually
    //       turn the patch into an absolute instruction.
    //       I believe, this is no short term change, but we should strongly plan this, and do it.
    //       Technically, we need to be able to add multiple storage handlers into a single pipeline,
    //       and except for the final storage handler, no other handler should bother about where a
    //       feature is stored (unless itself has a specific reason). Therefore, lets change this and
    //       send logical requests through the pipeline, with logical responses. This even makes testing
    //       simpler, because a mock for a storage does only need to respond to the logical request with
    //       a logical answer.
    //       NOTE: The reason the `naksha.model.request.WriteRequest` is a JSON object is that handlers
    //       can generate it, then serialize it, and send it to remove physical storage systems. This is
    //       as well recommended for logical requests/responses, why both could extend the very basic
    //       `naksha.model.request.Request` and `naksha.model.request.Response`.

    // as applicable, modify features based on parameters supplied
    final WriteRequest wrRequest = new WriteRequest();
    for (final NakshaFeature feature : features) {
      modifyTagsFromFeature(feature, addTags, removeTags);
      if(hasSpecifiedVersion(feature)) {
        // version defined - perform atomic update including version validation
        wrRequest.add(new Write().updateFeature(null, spaceId, feature, true));
      } else {
        // no version - overwrite the feature, regardless whether it exists or not (forceful upsert)
        wrRequest.add(new Write().upsertFeature(null, spaceId, feature));
      }
    }

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformWriteResultToXyzCollectionResponse(response, NakshaFeature.class, false);
  }

  private @NotNull XyzResponse executeUpdateFeature() {
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
    modifyTagsFromFeature(feature, addTags, removeTags);
    WriteRequest writeRequest;
    if(hasSpecifiedVersion(feature)) {
      // version defined - perform atomic update including version validation
      writeRequest = atomicUpdateFeatureRequest(null, spaceId, feature);
    } else {
      // no version - perform non atomic update (force update, without version check)
      writeRequest = nonAtomicUpdateFeatureRequest(null, spaceId, feature);
    }

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(writeRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformResponseToXyzFeatureResponse(response, NakshaFeature.class, NoElementsStrategy.FAIL_ON_NO_ELEMENTS);
  }

  private boolean hasSpecifiedVersion(NakshaFeature feature) {
    return feature.getProperties().getXyz().getUuid() != null;
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

    final WriteRequest wrRequest = RequestHelper.deleteFeaturesByIdsRequest(null, spaceId, features);

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformWriteResultToXyzCollectionResponse(response, NakshaFeature.class, true);
  }

  private @NotNull XyzResponse executeDeleteFeature() {
    // Parse API parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final String featureId = ApiParams.extractMandatoryPathParam(routingContext, FEATURE_ID);

    // prepare request
    final WriteRequest wrRequest = RequestHelper.deleteFeatureByIdRequest(null, spaceId, featureId);

    // Forward request to NH Space Storage writer instance
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    // transform WriteResult to Http FeatureCollection response
    return transformResponseToXyzFeatureResponse(response, NakshaFeature.class, NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS);
  }

  private static final int MAX_RETRY_ATTEMPT = 5;

  private @NotNull XyzResponse executePatchFeatureById() {

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

    // Extract ids so that we can fetch existing features
    final List<String> requestFeaturesIds = new ArrayList<>();
    for (NakshaFeature feature : featuresFromRequest) {
      requestFeaturesIds.add(feature.getId());
    }

    // Fetch features that already exist in the storage
    final ReadFeatures getExistingFeatures = proxyWrapperOf(RequestHelper.readFeaturesByIdsRequest(null, spaceId, requestFeaturesIds))
        .withReadRequestType(ReadFeaturesProxyWrapper.ReadRequestType.GET_BY_IDS)
        .withQueryParameters(Map.of(FEATURE_IDS, requestFeaturesIds));
    Response existingFeaturesResp = executeReadRequestFromSpaceStorage(getExistingFeatures);

    // Handle response - group existing features by id (optimize subsequent traversals)
    MapProxy<String, NakshaFeature> existingFeaturesById;
    if (existingFeaturesResp instanceof SuccessResponse successResponse) {
      existingFeaturesById = ResultHelper.extractAndGroupAllFeaturesById(successResponse, NakshaFeature.class);
    } else if (existingFeaturesResp instanceof ErrorResponse errorResponse) {
      logger.error("Error encountered while reading features from storage. Feature ids: {}, error: {}", requestFeaturesIds,
          errorResponse.getError());
      return verticle.sendErrorResponse(routingContext, errorResponse.getError());
    } else {
      logger.error("Unexpected response while reading features from storage. Feature ids: {}, unknown response: {}", requestFeaturesIds,
          existingFeaturesResp);
      return verticle.sendErrorResponse(routingContext,
          new NakshaError(NakshaError.EXCEPTION, "Unexpected response while reading features from storage: " + existingFeaturesResp));
    }
    if (existingFeaturesById.isEmpty()) {
      if (responseType == HttpResponseType.FEATURE) {
        logger.error(
            "Unexpected null result while reading current versions in storage of targeted features for PATCH. The feature ({}) does not exist.",
            requestFeaturesIds);
        return verticle.sendErrorResponse(routingContext, new NakshaError(NakshaError.NOT_FOUND, "Feature does not exist."));
      } else if (!responseType.equals(HttpResponseType.FEATURE_COLLECTION)) {
        logger.error("Unsupported HttpResponseType was called: {}", responseType);
        return verticle.sendErrorResponse(routingContext, new NakshaError(NakshaError.EXCEPTION, "Internal server error."));
      }
    }

    // Prepare WriteRequest - separating insert from updates and keeping the order of the features from the request
    WriteRequest insertsAndUpdates = new WriteRequest();
    for (NakshaFeature featureFromRequest : featuresFromRequest) {
      NakshaFeature correspondingExistingFeature = existingFeaturesById.get(featureFromRequest.getId());
      if (correspondingExistingFeature == null) {
        // Feature not yet persisted - just insert
        modifyTagsFromFeature(featureFromRequest, addTags, removeTags);
        insertsAndUpdates.add(new Write().createFeature(null, spaceId, featureFromRequest));
      } else {
        // Feature exists - prepare patch for update (atomic == true, we want version validation)
        NakshaFeature patchedFeature = patchedFeature(featureFromRequest, correspondingExistingFeature);
        modifyTagsFromFeature(patchedFeature, addTags, removeTags);
        insertsAndUpdates.add(new Write().updateFeature(null, spaceId, patchedFeature, true));
      }
    }

    // Forward request to NH Space Storage writer instance
    return naksha().getSpaceStorage().useWriteSession(SessionOptions.from(context(), true), writer -> {
      final Response wrResponse = writer.execute(insertsAndUpdates);
      if (wrResponse == null) {
        // unexpected null response
        writer.rollback();
        writer.close();
        return verticle.sendErrorResponse(routingContext, new NakshaError(NakshaError.EXCEPTION, "Unexpected null result."));
      } else if (wrResponse instanceof ErrorResponse er) {
        writer.rollback();
        writer.close();
        // If the error was due to CONFLICT we assume concurrent modification and retry
        if (NakshaError.CONFLICT.equals(er.getError())) {
          if (retry >= MAX_RETRY_ATTEMPT) {
            NakshaError error = er.getError();
            String msg = "Max retry attempt for PATCH REST API reached, too many concurrent modification, error: " + error;
            logger.error(msg, er.getError().getCause());
            return verticle.sendErrorResponse(routingContext, new NakshaError(NakshaError.EXCEPTION, msg));
          }
          return attemptFeaturesPatching(
              spaceId, featuresFromRequest, responseType, addTags, removeTags, retry + 1);
        } else {
          logger.error("Received error result {}", er);
          return verticle.sendErrorResponse(routingContext, er.getError());
        }
      } else {
        if (responseType.equals(HttpResponseType.FEATURE)) {
          return transformResponseToXyzFeatureResponse(wrResponse, NakshaFeature.class, NoElementsStrategy.FAIL_ON_NO_ELEMENTS);
        }
        return transformWriteResultToXyzCollectionResponse(wrResponse, NakshaFeature.class, false);
      }
    });
  }

  private NakshaFeature patchedFeature(
      @NotNull NakshaFeature featureFromRequest,
      @NotNull NakshaFeature featureToPatchFromStorage
  ) {
    Difference difference = DifferenceCalculator.calculateDifference(featureToPatchFromStorage, featureFromRequest);
    DifferenceFilter.removeAllRemoveOp(difference);
    return Patcher.patch(featureToPatchFromStorage, difference);
  }

  private void modifyTagsFromFeature(NakshaFeature feature, List<String> tagsToBeAdded, List<String> tagsToBeRemoved) {
    XyzNs xyzNamespace = feature.getProperties().getXyz();
    xyzNamespace.addTags(tagsToBeAdded, true);
    xyzNamespace.removeTags(tagsToBeRemoved, true);
  }
}
