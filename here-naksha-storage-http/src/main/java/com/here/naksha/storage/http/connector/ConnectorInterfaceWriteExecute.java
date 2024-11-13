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
import com.here.naksha.lib.core.models.payload.events.feature.*;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public class ConnectorInterfaceWriteExecute {

  @NotNull
  public static Result execute(NakshaContext context, WriteXyzFeatures request, RequestSender sender) {
    String streamId = context.getStreamId();
    String connectorSpaceName = request.getCollectionId();
    Space connectorSpace = new Space(connectorSpaceName);

    Event event = createCreateFeaturesEvent(context, request, sender, connectorSpaceName);

    event.setSpace(connectorSpace);
    event.setStreamId(streamId);

    String jsonEvent = JsonSerializable.serialize(event);
    HttpResponse<byte[]> httpResponse = sender.post(jsonEvent);

    return PrepareResult.prepareWriteResult(httpResponse);
  }

  private static ModifyFeaturesEvent createCreateFeaturesEvent(
      NakshaContext context, WriteXyzFeatures request, RequestSender sender, String connectorSpaceName) {
    ModifyFeaturesEvent event = new ModifyFeaturesEvent();

    List<XyzFeature> featuresToInsert = new LinkedList<>();
    List<XyzFeature> featuresToUpdate = new LinkedList<>();

    for (XyzFeatureCodec featureCoded : request.features) {
      XyzFeature feature = featureCoded.getFeature();
      if (isNewFeature(context, feature, sender, connectorSpaceName)) {
        featuresToInsert.add(feature);
      } else {
        featuresToUpdate.add(feature);
      }
    }

    long creationTime = System.currentTimeMillis();
    featuresToInsert.forEach(feature -> {
      setRandomUuid(feature);
      setCreatedAt(feature, creationTime);
      setUpdatedAt(feature, creationTime);
    });
    featuresToUpdate.forEach(feature -> {
      assertUuidMatch(context, feature, sender, connectorSpaceName);
      setPuuidFromUuid(context, feature, sender, connectorSpaceName);
      setRandomUuid(feature);
      fillMissingCreatedAt(context, feature, sender, connectorSpaceName);
      setUpdatedAt(feature, creationTime);
    });

    event.setInsertFeatures(featuresToInsert);
    event.setUpdateFeatures(featuresToUpdate);
    event.setDeleteFeatures(Map.of()); // Connector requires empty map instead of no-field or null

    return event;
  }

  private static void assertUuidMatch(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    String uuid = feature.getProperties().getXyzNamespace().getUuid();
    if (uuid != null) {
      String uuidFromDb = getUuidFromDb(context, feature, sender, connectorSpaceName);
      if (!uuid.equals(uuidFromDb)) {
        throw new ConflictException(
            "The feature with id %s cannot be replaced. The provided UUID doesn't match the UUID of the head state: %s"
                .formatted(feature.getId(), uuidFromDb));
      }
    }
  }

  private static void setCreatedAt(XyzFeature feature, long creationTime) {
    feature.getProperties().getXyzNamespace().setCreatedAt(creationTime);
  }

  private static void fillMissingCreatedAt(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    XyzNamespace xyzNamespace = feature.getProperties().getXyzNamespace();
    if (xyzNamespace.getCreatedAt() <= 0) {
      xyzNamespace.setCreatedAt(getCreatedAtFromDb(context, feature, sender, connectorSpaceName));
    }
  }

  private static void setUpdatedAt(XyzFeature feature, long creationTime) {
    feature.getProperties().getXyzNamespace().setUpdatedAt(creationTime);
  }

  private static void setRandomUuid(XyzFeature feature) {
    feature.getProperties().getXyzNamespace().setUuid(UUID.randomUUID().toString());
  }

  private static void setPuuidFromUuid(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    XyzNamespace xyzNamespace = feature.getProperties().getXyzNamespace();
    String uuid = xyzNamespace.getUuid();
    if (uuid == null) {
      uuid = getUuidFromDb(context, feature, sender, connectorSpaceName);
    }
    xyzNamespace.setPuuid(uuid);
  }

  private static boolean isNewFeature(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    String uuid = feature.getProperties().getXyzNamespace().getUuid();
    // We are making an assumption that if uuid exists in feature, it is not a new feature
    return uuid == null && !existsInDb(context, feature, sender, connectorSpaceName);
  }

  private static boolean existsInDb(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    try {
      Result result = getFeatureFromDb(context, feature, sender, connectorSpaceName);
      return result.getXyzFeatureCursor().hasNext();
    } catch (NoCursor e) {
      throw new RuntimeException(e);
    }
  }

  private static long getCreatedAtFromDb(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    try {
      Result result = getFeatureFromDb(context, feature, sender, connectorSpaceName);
      ForwardCursor<XyzFeature, XyzFeatureCodec> xyzFeatureCursor = result.getXyzFeatureCursor();
      xyzFeatureCursor.next();
      return xyzFeatureCursor
          .getFeature()
          .getProperties()
          .getXyzNamespace()
          .getCreatedAt();
    } catch (NoCursor e) {
      throw new RuntimeException(e);
    }
  }

  private static String getUuidFromDb(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    try {
      Result result = getFeatureFromDb(context, feature, sender, connectorSpaceName);
      ForwardCursor<XyzFeature, XyzFeatureCodec> xyzFeatureCursor = result.getXyzFeatureCursor();
      xyzFeatureCursor.next();
      return xyzFeatureCursor
          .getFeature()
          .getProperties()
          .getXyzNamespace()
          .getUuid();
    } catch (NoCursor e) {
      throw new RuntimeException(e);
    }
  }

  private static Result getFeatureFromDb(
      NakshaContext context, XyzFeature feature, RequestSender sender, String connectorSpaceName) {
    ReadFeaturesProxyWrapper getFeaturesRequest = new ReadFeaturesProxyWrapper().withReadRequestType(GET_BY_ID);
    getFeaturesRequest.addQueryParameter(FEATURE_ID, feature.getId());
    getFeaturesRequest.addCollection(connectorSpaceName);
    return ConnectorInterfaceReadExecute.execute(context, getFeaturesRequest, sender);
  }

  public static class ConflictException extends IllegalStateException {
    public ConflictException(String message) {
      super(message);
    }
  }
}
