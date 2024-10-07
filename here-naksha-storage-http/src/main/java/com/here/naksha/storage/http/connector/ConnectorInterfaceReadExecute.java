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

import static com.here.naksha.common.http.apis.ApiParamsConst.*;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.geojson.coordinates.BBox;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.events.PropertyQueryOr;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByBBoxEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByIdEvent;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import java.net.http.HttpResponse;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

public class ConnectorInterfaceReadExecute {

  @NotNull
  public static Result execute(NakshaContext context, ReadFeaturesProxyWrapper request, RequestSender sender) {
    String streamId = context.getStreamId();
    String dataHubSpaceName = request.getCollections().get(0);
    Space dataHubSpace = new Space(dataHubSpaceName);

    Event event =
        switch (request.getReadRequestType()) {
          case GET_BY_ID -> createFeatureByIdEvent(request);
          case GET_BY_IDS -> createFeaturesByIdsEvent(request);
          case GET_BY_BBOX -> createFeatureByBBoxEvent(request);
          case GET_BY_TILE -> createFeaturesByTileEvent(request);
          default -> throw new IllegalStateException("Unexpected value: " + request.getReadRequestType());
        };

    event.setSpace(dataHubSpace);
    event.setStreamId(streamId);

    String jsonEvent = JsonSerializable.serialize(event);
    HttpResponse<byte[]> httpResponse = post(sender, jsonEvent);

    return PrepareResult.prepareResult(httpResponse, XyzFeatureCollection.class, XyzFeatureCollection::getFeatures);
  }

  private static Event createFeaturesByIdsEvent(ReadFeaturesProxyWrapper request) {
    List<String> id = request.getQueryParameter(FEATURE_IDS);
    return new GetFeaturesByIdEvent().withIds(id);
  }

  private static Event createFeatureByIdEvent(ReadFeaturesProxyWrapper request) {
    String id = request.getQueryParameter(FEATURE_ID);
    return new GetFeaturesByIdEvent().withIds(List.of(id));
  }

  private static Event createFeatureByBBoxEvent(ReadFeaturesProxyWrapper request) {
    BBox bBox = new BBox(
        request.getQueryParameter(WEST),
        request.getQueryParameter(SOUTH),
        request.getQueryParameter(EAST),
        request.getQueryParameter(NORTH));
    Long limit = request.getQueryParameter(LIMIT);
    boolean clip = request.getQueryParameter(CLIP_GEO);
    POp propertyOp = request.getPropertyOp();

    GetFeaturesByBBoxEvent getFeaturesByBBoxEvent = new GetFeaturesByBBoxEvent();
    getFeaturesByBBoxEvent.setLimit(limit);
    getFeaturesByBBoxEvent.setBbox(bBox);
    getFeaturesByBBoxEvent.setClip(clip);
    if (propertyOp != null) {
      PropertyQueryOr propertiesQuery = new PropertyQueryOr();
      propertiesQuery.add(POpToQueryConverter.pOpToQuery(propertyOp));
      getFeaturesByBBoxEvent.setPropertiesQuery(propertiesQuery);
    }

    return getFeaturesByBBoxEvent;
  }

  private static Event createFeaturesByTileEvent(ReadFeaturesProxyWrapper request) throws NotImplementedException {
    //    Long margin = request.getQueryParameter(MARGIN);
    //    Long limit = request.getQueryParameter(LIMIT);
    //    String tileType = request.getQueryParameter(TILE_TYPE);
    //    String tileId = request.getQueryParameter(TILE_ID);
    //
    //    if (tileType != null && !tileType.equals(TILE_TYPE_QUADKEY))
    //      throw new NotImplementedException("Tile type other than " + TILE_TYPE_QUADKEY);

    //
    //    GetFeaturesByTileEvent getFeaturesByTileEvent = new GetFeaturesByTileEvent();
    //    getFeaturesByTileEvent.setHereTileFlag(false);
    //    getFeaturesByTileEvent.setMargin(margin.intValue());
    //    getFeaturesByTileEvent.setLimit(limit);

    throw new NotImplementedException();
  }

  private static HttpResponse<byte[]> post(RequestSender sender, String body) {
    return sender.sendRequest("", true, null, "POST", body);
  }
}
