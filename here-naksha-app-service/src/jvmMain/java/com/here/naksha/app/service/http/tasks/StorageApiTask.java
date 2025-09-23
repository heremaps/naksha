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

import static com.here.naksha.app.service.http.ops.CommonPropertiesToMask.COMMON_PROPERTIES_TO_MASK;
import static com.here.naksha.app.service.http.ops.MaskingUtil.maskProperties;
import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.FAIL_ON_NO_ELEMENTS;
import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS;
import static com.here.naksha.common.http.apis.ApiParamsConst.STORAGE_ID;
import static com.here.naksha.lib.core.HubInternalIdentifiers.STORAGES;

import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.apis.ApiParams;
import com.here.naksha.app.service.http.ops.CommonPropertiesToMask;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import io.vertx.ext.web.RoutingContext;
import java.util.Set;
import naksha.base.JvmJsonUtil;
import naksha.base.StringList;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.WriteRequest;
import naksha.model.util.RequestHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageApiTask extends AbstractApiTask<XyzResponse> {

  private static final Logger logger = LoggerFactory.getLogger(StorageApiTask.class);

  private final @NotNull StorageApiReqType reqType;

  public enum StorageApiReqType {
    GET_ALL_STORAGES,
    GET_STORAGE_BY_ID,
    CREATE_STORAGE,
    UPDATE_STORAGE,
    DELETE_STORAGE
  }

  public StorageApiTask(
      final @NotNull StorageApiReqType reqType,
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
        case GET_ALL_STORAGES -> executeGetStorages();
        case GET_STORAGE_BY_ID -> executeGetStorageById();
        case CREATE_STORAGE -> executeCreateStorage();
        case UPDATE_STORAGE -> executeUpdateStorage();
        case DELETE_STORAGE -> executeDeleteStorage();
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

  private @NotNull XyzResponse executeGetStorages() {
    final ReadFeatures request = new ReadFeatures().addCollectionId(STORAGES);
    request.setMapId(naksha().getAdminMapId());
    Response response = executeReadRequestFromSpaceStorage(request);
    return transformResponseToXyzCollectionResponse(response, NakshaStorage.class, this::maskSensitiveProperties);
  }

  private @NotNull XyzResponse executeGetStorageById() {
    final String storageId = ApiParams.extractMandatoryPathParam(routingContext, STORAGE_ID);
    final ReadFeatures request = new ReadFeatures().addCollectionId(STORAGES);
    request.setMapId(naksha().getAdminMapId());
    request.setFeatureIds(StringList.of(storageId));
    return transformedResponseTo(request);
  }

  private @NotNull XyzResponse executeCreateStorage() {
    final NakshaStorage newStorage = storageConfigFromRequestBody();
    final WriteRequest wrRequest = RequestHelper.createFeatureRequest(naksha().getAdminMapId(), STORAGES, newStorage);
    return transformedResponseTo(wrRequest);
  }

  private @NotNull XyzResponse executeUpdateStorage() {
    final String storageIdFromPath = ApiParams.extractMandatoryPathParam(routingContext, STORAGE_ID);
    final NakshaStorage storageFromBody = storageConfigFromRequestBody();
    if (!storageFromBody.getId().equals(storageIdFromPath)) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, mismatchMsg(storageIdFromPath, storageFromBody));
    } else {
      final WriteRequest updateStorageReq = RequestHelper.nonAtomicUpdateFeatureRequest(naksha().getAdminMapId(), STORAGES, storageFromBody);
      return transformedResponseTo(updateStorageReq);
    }
  }

  private @NotNull XyzResponse executeDeleteStorage() {
    final String storageId = ApiParams.extractMandatoryPathParam(routingContext, STORAGE_ID);
    final WriteRequest wrRequest = RequestHelper.deleteFeatureByIdRequest(naksha().getAdminMapId(), STORAGES, storageId);
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    return transformResponseToXyzFeatureResponse(response, NakshaStorage.class, NOT_FOUND_ON_NO_ELEMENTS,
        this::maskSensitiveProperties);
  }

  @NotNull
  private XyzResponse transformedResponseTo(ReadFeatures request) {
    Response response = executeReadRequestFromSpaceStorage(request);
    return transformResponseToXyzFeatureResponse(
        response, NakshaStorage.class, NOT_FOUND_ON_NO_ELEMENTS, this::maskSensitiveProperties);
  }

  @NotNull
  private XyzResponse transformedResponseTo(WriteRequest updateStorageReq) {
    Response updateStorageResult = executeWriteRequestFromSpaceStorage(updateStorageReq);
    return transformResponseToXyzFeatureResponse(updateStorageResult, NakshaStorage.class, FAIL_ON_NO_ELEMENTS,
        this::maskSensitiveProperties);
  }

  private NakshaStorage maskSensitiveProperties(NakshaStorage storageConfig) {
    maskProperties(storageConfig, COMMON_PROPERTIES_TO_MASK);
    return storageConfig;
  }

  private @NotNull NakshaStorage storageConfigFromRequestBody() {
    final String bodyJson = routingContext.body().asString();
    return JvmJsonUtil.readJsonAs(bodyJson, NakshaStorage.class);
  }

  private static String mismatchMsg(String storageIdFromPath, NakshaStorage storageConfigFromBody) {
    return "Mismatch between storage ids. Path storage id: %s, body storage id: %s"
        .formatted(storageIdFromPath, storageConfigFromBody.getId());
  }
}
