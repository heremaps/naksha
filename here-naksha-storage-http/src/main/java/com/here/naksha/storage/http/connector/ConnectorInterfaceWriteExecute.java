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

import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;
import static com.here.naksha.lib.core.models.storage.EWriteOp.*;
import static com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper.ReadRequestType.GET_BY_IDS;

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

  private final NakshaContext context;
  private final WriteXyzFeatures request;
  private final RequestSender sender;
  private final String connectorSpaceName;
  private final Map<String, XyzFeature> databaseFeaturesCache = new HashMap<>();

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

    populateDbCache(request.features);

    for (XyzFeatureCodec featureCodec : request.features) {
      XyzFeature feature = featureCodec.getFeature();
      if (featureCodec.getOp().equals(PUT.value())) {
        if (isNewFeature(feature)) {
          featuresToInsert.add(feature);
        } else {
          featuresToUpdate.add(feature);
        }
      } else if (featureCodec.getOp().equals(DELETE.value())) {
        // Connector docs requires map entry value to be null,
        // but in reality, doesn't matter what is the value
        // and map with null is ignored by JsonSerializable.serialize(),
        // so empty string is used instead.
        featuresToDelete.put(feature.getId(), "");
      } else {
        throw new UnsupportedOperationException("Unsupported feature codec OP: " + featureCodec.getOp());
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

  /**
   * Fills cache with features from database.
   */
  private void populateDbCache(List<XyzFeatureCodec> features) {
    List<String> idsList =
        features.stream().map(feature -> feature.getFeature().getId()).toList();
    ForwardCursor<XyzFeature, XyzFeatureCodec> xyzFeatureCursor = getFeaturesFromDb(idsList);
    while (xyzFeatureCursor.next()) {
      XyzFeature featureFromDb = xyzFeatureCursor.getFeature();
      databaseFeaturesCache.put(featureFromDb.getId(), featureFromDb);
    }
  }

  private void assertUuidMatch(XyzFeature feature) {
    String uuid = feature.getProperties().getXyzNamespace().getUuid();
    if (uuid != null) {
      String uuidFromDb = getXyzNamespaceFromDbCache(feature).getUuid();
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
      xyzNamespace.setCreatedAt(getXyzNamespaceFromDbCache(feature).getCreatedAt());
    }
  }

  private void setPuuidFromUuid(XyzFeature feature) {
    XyzNamespace xyzNamespace = feature.getProperties().getXyzNamespace();
    String uuid = xyzNamespace.getUuid();
    if (uuid == null) {
      uuid = getXyzNamespaceFromDbCache(feature).getUuid();
    }
    xyzNamespace.setPuuid(uuid);
  }

  private boolean isNewFeature(XyzFeature feature) {
    String uuid = feature.getProperties().getXyzNamespace().getUuid();
    // We are making an assumption that if uuid exists in feature, it is not a new feature
    return uuid == null && !existsInDb(feature);
  }

  private boolean existsInDb(XyzFeature feature) {
    return databaseFeaturesCache.containsKey(feature.getId());
  }

  private XyzNamespace getXyzNamespaceFromDbCache(XyzFeature feature) {
    return getFeatureFromDbCache(feature).getProperties().getXyzNamespace();
  }

  private @Nullable XyzFeature getFeatureFromDbCache(XyzFeature feature) {
    if (databaseFeaturesCache.containsKey(feature.getId())) {
      return databaseFeaturesCache.get(feature.getId());
    } else {
      throw new IllegalStateException("Feature with id " + feature.getId() + " not present in cache");
    }
  }

  private ForwardCursor<XyzFeature, XyzFeatureCodec> getFeaturesFromDb(List<String> featureIds) {
    ReadFeaturesProxyWrapper getFeaturesRequest = new ReadFeaturesProxyWrapper().withReadRequestType(GET_BY_IDS);
    getFeaturesRequest.addQueryParameter(FEATURE_IDS, featureIds);
    getFeaturesRequest.addCollection(connectorSpaceName);
    try (Result result = ConnectorInterfaceReadExecute.execute(context, getFeaturesRequest, sender)) {
      return result.getXyzFeatureCursor();
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
