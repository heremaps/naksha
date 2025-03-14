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

import static com.here.naksha.app.service.http.apis.ApiParams.extractMandatoryPathParam;
import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS;
import static com.here.naksha.common.http.apis.ApiParamsConst.SPACE_ID;
import static com.here.naksha.lib.core.HubInternalIdentifiers.SPACES;
import static naksha.model.NakshaContext.mapId;

import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import io.vertx.ext.web.RoutingContext;
import naksha.base.FromJsonOptions;
import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import naksha.base.StringList;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.util.RequestHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpaceApiTask extends AbstractApiTask<XyzResponse> {

  private static final Logger logger = LoggerFactory.getLogger(SpaceApiTask.class);
  private final @NotNull SpaceApiReqType reqType;

  public enum SpaceApiReqType {
    GET_ALL_SPACES,
    GET_SPACE_BY_ID,
    CREATE_SPACE,
    UPDATE_SPACE,
    DELETE_SPACE
  }

  public SpaceApiTask(
      final @NotNull SpaceApiReqType reqType,
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
    try {
      return switch (this.reqType) {
        case CREATE_SPACE -> executeCreateSpace();
        case UPDATE_SPACE -> executeUpdateSpace();
        case GET_ALL_SPACES -> executeGetSpaces();
        case GET_SPACE_BY_ID -> executeGetSpaceById();
        case DELETE_SPACE -> executeDeleteSpace();
        default -> executeUnsupported();
      };
    } catch (NakshaException nakshaException) {
      logger.warn("Known exception while processing request. ", nakshaException);
      return verticle.sendErrorResponse(routingContext, nakshaException.getError());
    } catch (Exception ex) {
      logger.error("Unexpected error while processing request. ", ex);
      return verticle.sendErrorResponse(
          routingContext, NakshaError.EXCEPTION, "Internal error : " + ex.getMessage());
    }
  }

  private XyzResponse executeDeleteSpace() {
    final String spaceId = extractMandatoryPathParam(routingContext, SPACE_ID);
    final WriteRequest wr = new WriteRequest().add(new Write().deleteFeatureById(mapId(), spaceId, SPACES));

    Response response = executeWriteRequestFromSpaceStorage(wr);
    return transformResponseToXyzFeatureResponse(response, NakshaFeature.class, NOT_FOUND_ON_NO_ELEMENTS);
  }

  private @NotNull XyzResponse executeCreateSpace() {
    final Space newSpace = spaceFromRequestBody();
    final WriteRequest wrRequest = RequestHelper.createFeatureRequest(SPACES, newSpace);
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    return transformResponseToXyzFeatureResponse(response, Space.class, NoElementsStrategy.FAIL_ON_NO_ELEMENTS);
  }

  private @NotNull XyzResponse executeUpdateSpace() {
    final String spaceIdFromPath = extractMandatoryPathParam(routingContext, SPACE_ID);
    final Space spaceFromBody = spaceFromRequestBody();
    if (!spaceFromBody.getId().equals(spaceIdFromPath)) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, mismatchMsg(spaceIdFromPath, spaceFromBody));
    } else {
      final WriteRequest updateSpaceReq = RequestHelper.updateFeatureRequest(SPACES, spaceFromBody);
      Response updateSpaceResponse = executeWriteRequestFromSpaceStorage(updateSpaceReq);
      return transformResponseToXyzFeatureResponse(updateSpaceResponse, Space.class, NoElementsStrategy.FAIL_ON_NO_ELEMENTS);
    }
  }

  private @NotNull XyzResponse executeGetSpaces() {
    final ReadFeatures request = new ReadFeatures().addCollectionId(SPACES);
    Response response = executeReadRequestFromSpaceStorage(request);
    return transformResponseToXyzCollectionResponse(response, Space.class);
  }

  private @NotNull XyzResponse executeGetSpaceById() {
    final String spaceId = extractMandatoryPathParam(routingContext, SPACE_ID);
    final ReadFeatures request = new ReadFeatures().addCollectionId(SPACES);
    request.setFeatureIds(StringList.of(spaceId));
    Response response = executeReadRequestFromSpaceStorage(request);
    return transformResponseToXyzFeatureResponse(response, Space.class, NOT_FOUND_ON_NO_ELEMENTS);
  }

  private Space spaceFromRequestBody() {
    final String bodyJson = routingContext.body().asString();
    return JvmBoxingUtil.box(Platform.fromJSON(bodyJson, FromJsonOptions.DEFAULT), Space.class);
  }

  private static String mismatchMsg(String spaceIdFromPath, Space spaceFromBody) {
    return "Mismatch between space ids. Path space id: %s, body space id: %s"
        .formatted(spaceIdFromPath, spaceFromBody.getId());
  }
}
