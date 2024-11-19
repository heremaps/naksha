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
package com.here.naksha.storage.http.connector;

import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_ID;
import static com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper.ReadRequestType.GET_BY_ID;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.events.feature.ModifyFeaturesEvent;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import java.net.http.HttpResponse;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConnectorInterfaceWriteExecute {

  public static final String PUT_OP = "PUT";
  public static final String DELETE_OP = "DELETE";
  private final NakshaContext context;
  private final WriteXyzFeatures request;
  private final RequestSender sender;
  private final String connectorSpaceName;
  private final Map<String, XyzFeature> featuresCache = new HashMap<>();

  public ConnectorInterfaceWriteExecute(NakshaContext context, WriteXyzFeatures request, RequestSender sender) {
    this.context = context;
    this.request = request;
    this.sender = sender;
    this.connectorSpaceName = request.getCollectionId();
  }

  private static void setCreatedAt(XyzFeature feature, long creationTime) {
    feature.getProperties().getXyzNamespace().setCreatedAt(creationTime);
  }

  private static void setUpdatedAt(XyzFeature feature, long creationTime) {
    feature.getProperties().getXyzNamespace().setUpdatedAt(creationTime);
  }

  private static void setRandomUuid(XyzFeature feature) {
    feature.getProperties().getXyzNamespace().setUuid(UUID.randomUUID().toString());
  }

  @NotNull
  public Result execute() {
    String streamId = context.getStreamId();
    Space connectorSpace = new Space(connectorSpaceName);

    Event event = createModifyFeaturesEvent();

    event.setSpace(connectorSpace);
    event.setStreamId(streamId);

    String jsonEvent = JsonSerializable.serialize(event);
    HttpResponse<byte[]> httpResponse = sender.post(jsonEvent);

    return PrepareResult.prepareWriteResult(httpResponse);
  }

  private ModifyFeaturesEvent createModifyFeaturesEvent() {
    ModifyFeaturesEvent event = new ModifyFeaturesEvent();

    List<XyzFeature> featuresToInsert = new LinkedList<>();
    List<XyzFeature> featuresToUpdate = new LinkedList<>();
    Map<String, String> featuresToDelete = new HashMap<>(); // Format enforced by connector API

    for (XyzFeatureCodec featureCodec : request.features) {
      XyzFeature feature = featureCodec.getFeature();
      switch (featureCodec.getOp()) {
        case PUT_OP -> {
          if (isNewFeature(feature)) {
            featuresToInsert.add(feature);
          } else {
            featuresToUpdate.add(feature);
          }
        }
        case DELETE_OP -> {
          // Connector docs requires map entry value to be null,
          // but in reality, doesn't matter what is the value
          // and map with null is ignored by JsonSerializable.serialize(),
          // so empty string is used instead.
          featuresToDelete.put(feature.getId(), "");
        }
        default -> throw new UnsupportedOperationException(
            "Unsupported feature codec OP: " + featureCodec.getOp());
      }
    }

    long currentTime = System.currentTimeMillis();
    featuresToInsert.forEach(feature -> {
      setRandomUuid(feature);
      setCreatedAt(feature, currentTime);
      setUpdatedAt(feature, currentTime);
    });
    featuresToUpdate.forEach(feature -> {
      assertUuidMatch(feature);
      setPuuidFromUuid(feature);
      setRandomUuid(feature);
      fillMissingCreatedAt(feature);
      setUpdatedAt(feature, currentTime);
    });

    event.setInsertFeatures(featuresToInsert);
    event.setUpdateFeatures(featuresToUpdate);
    event.setDeleteFeatures(featuresToDelete);

    return event;
  }

  private void assertUuidMatch(XyzFeature feature) {
    String uuid = feature.getProperties().getXyzNamespace().getUuid();
    if (uuid != null) {
      String uuidFromDb = getUuidFromDb(feature);
      if (!uuid.equals(uuidFromDb)) {
        throw new ConflictException(
            "The feature with id %s cannot be replaced. The provided UUID doesn't match the UUID of the head state: %s"
                .formatted(feature.getId(), uuidFromDb));
      }
    }
  }

  private void fillMissingCreatedAt(XyzFeature feature) {
    XyzNamespace xyzNamespace = feature.getProperties().getXyzNamespace();
    if (xyzNamespace.getCreatedAt() <= 0) {
      xyzNamespace.setCreatedAt(getCreatedAtFromDb(feature));
    }
  }

  private void setPuuidFromUuid(XyzFeature feature) {
    XyzNamespace xyzNamespace = feature.getProperties().getXyzNamespace();
    String uuid = xyzNamespace.getUuid();
    if (uuid == null) {
      uuid = getUuidFromDb(feature);
    }
    xyzNamespace.setPuuid(uuid);
  }

  private boolean isNewFeature(XyzFeature feature) {
    String uuid = feature.getProperties().getXyzNamespace().getUuid();
    // We are making an assumption that if uuid exists in feature, it is not a new feature
    return uuid == null && !existsInDb(feature);
  }

  private boolean existsInDb(XyzFeature feature) {
    return getFeatureFromDb(feature) != null;
  }

  private long getCreatedAtFromDb(XyzFeature feature) {
    return getFeatureFromDb(feature).getProperties().getXyzNamespace().getCreatedAt();
  }

  private String getUuidFromDb(XyzFeature feature) {
    return getFeatureFromDb(feature).getProperties().getXyzNamespace().getUuid();
  }

  private @Nullable XyzFeature getFeatureFromDb(XyzFeature featureFromRequest) {
    if (featuresCache.containsKey(featureFromRequest.getId())) return featuresCache.get(featureFromRequest.getId());
    ReadFeaturesProxyWrapper getFeaturesRequest = new ReadFeaturesProxyWrapper().withReadRequestType(GET_BY_ID);
    getFeaturesRequest.addQueryParameter(FEATURE_ID, featureFromRequest.getId());
    getFeaturesRequest.addCollection(connectorSpaceName);
    try (Result result = ConnectorInterfaceReadExecute.execute(context, getFeaturesRequest, sender)) {
      ForwardCursor<XyzFeature, XyzFeatureCodec> xyzFeatureCursor = result.getXyzFeatureCursor();
      if (xyzFeatureCursor.next()) {
        XyzFeature featureFromDb = xyzFeatureCursor.getFeature();
        featuresCache.put(featureFromDb.getId(), featureFromDb);
        return featureFromDb;
      } else {
        return null;
      }
    } catch (NoCursor e) {
      throw new RuntimeException(e);
    }
  }

  public static class ConflictException extends IllegalStateException {
    public ConflictException(String message) {
      super(message);
    }
  }
}
