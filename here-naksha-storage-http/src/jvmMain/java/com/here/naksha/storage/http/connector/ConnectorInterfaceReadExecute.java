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

import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.events.PropertyQueryOr;
import com.here.naksha.lib.core.models.payload.events.TagsQuery;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByBBoxEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByIdEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByTileEvent;
import com.here.naksha.lib.core.models.payload.events.feature.IterateFeaturesEvent;
import com.here.naksha.lib.core.models.payload.events.feature.QueryEvent;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import com.here.naksha.storage.http.connector.pop.IPropertyQueryToPropertiesQuery;
import com.here.naksha.storage.http.connector.pop.ITagQueryToTagsQuery;
import naksha.base.StringList;
import naksha.geo.SpBoundingBox;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.request.RequestQuery;
import naksha.model.request.Response;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpResponse;
import java.util.List;

import static com.here.naksha.common.http.apis.ApiParamsConst.CLIP_GEO;
import static com.here.naksha.common.http.apis.ApiParamsConst.EAST;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;
import static com.here.naksha.common.http.apis.ApiParamsConst.LIMIT;
import static com.here.naksha.common.http.apis.ApiParamsConst.MARGIN;
import static com.here.naksha.common.http.apis.ApiParamsConst.NORTH;
import static com.here.naksha.common.http.apis.ApiParamsConst.SOUTH;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_TYPE;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_TYPE_QUADKEY;
import static com.here.naksha.common.http.apis.ApiParamsConst.WEST;

public class ConnectorInterfaceReadExecute {

    @NotNull
    public static Response execute(naksha.model.NakshaContext context, ReadFeaturesProxyWrapper request, RequestSender sender) {
        String streamId = context.getStreamId();
        String endpoint = "/" + firstCollectionIdOrThrow(request);

        Event event;
        switch (request.getReadRequestType()) {
            case GET_BY_ID:
                event = createFeatureByIdEvent(request);
                break;
            case GET_BY_IDS:
                event = createFeaturesByIdsEvent(request);
                break;
            case GET_BY_BBOX:
                event = createFeatureByBBoxEvent(request);
                break;
            case GET_BY_TILE:
                event = createFeaturesByTileEvent(request);
                break;
            case ITERATE:
                event = createIterateEvent(request);
                break;
            default:
                throw new IllegalStateException("Unsupported read request type: " + request.getReadRequestType());
        }

        event.setStreamId(streamId);

        String jsonEvent = JsonSerializable.serialize(event);
        HttpResponse<byte[]> httpResponse = sender.post(endpoint, jsonEvent);

        return PrepareResult.prepareResult(
                httpResponse, PrepareResult.collectionMapper);
    }

    private static Event createIterateEvent(ReadFeaturesProxyWrapper request) {
        Integer limit = request.getQueryParameter(LIMIT);
        IterateFeaturesEvent event = new IterateFeaturesEvent();
        event.setLimit(limit.longValue());
        return event;
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
        SpBoundingBox bBox = new SpBoundingBox(
                request.getQueryParameter(WEST),
                request.getQueryParameter(SOUTH),
                request.getQueryParameter(EAST),
                request.getQueryParameter(NORTH));
        Integer limit = request.getQueryParameter(LIMIT);
        boolean clip = request.getQueryParameter(CLIP_GEO);

        GetFeaturesByBBoxEvent getFeaturesByBBoxEvent = new GetFeaturesByBBoxEvent();
        getFeaturesByBBoxEvent.setLimit(limit.longValue());
        getFeaturesByBBoxEvent.setBbox(bBox);
        getFeaturesByBBoxEvent.setClip(clip);
        setPropertyQuery(request, getFeaturesByBBoxEvent);

        return getFeaturesByBBoxEvent;
    }

    static void setPropertyQuery(naksha.model.request.ReadFeatures request, QueryEvent getFeaturesByBBoxEvent) {
        RequestQuery query = request.getQuery();
        if (query.getTags() != null) {
            TagsQuery tagsQuery = ITagQueryToTagsQuery.toTagsQuery(query.getTags());
            getFeaturesByBBoxEvent.setTags(tagsQuery);
        }
        if (query.getProperties() != null) {
            PropertyQueryOr popQueryOr = IPropertyQueryToPropertiesQuery.toPopQueryOr(query.getProperties());
            getFeaturesByBBoxEvent.setPropertiesQuery(popQueryOr);
        }
    }

    private static Event createFeaturesByTileEvent(ReadFeaturesProxyWrapper readRequest) {
        String tileType = readRequest.getQueryParameter(TILE_TYPE);
        if (TILE_TYPE_QUADKEY.equals(tileType)) {
            long margin = readRequest.getQueryParameter(MARGIN);
            Integer limit = readRequest.getQueryParameter(LIMIT);
            String tileId = readRequest.getQueryParameter(TILE_ID);
            boolean clip = readRequest.getQueryParameter(CLIP_GEO);

            GetFeaturesByTileEvent event = new GetFeaturesByTileEvent();
            event.setMargin((int) margin);
            event.setLimit(limit.longValue());
            event.setClip(clip);
            setPropertyQuery(readRequest, event);

            WebMercatorTile tileAddress = WebMercatorTile.forQuadkey(tileId);
            event.setBbox(tileAddress.getExtendedBBox(event.getMargin()));
            event.setLevel(tileAddress.level);
            event.setX(tileAddress.x);
            event.setY(tileAddress.y);
            event.setQuadkey(tileAddress.asQuadkey());
            return event;
        } else {
            throw new NakshaException(NakshaError.NOT_IMPLEMENTED,"Tile type other than " + TILE_TYPE_QUADKEY);
        }
    }

    private static String firstCollectionIdOrThrow(ReadFeaturesProxyWrapper request) {
        StringList ids = request.getCollectionId();
        if (ids == null || ids.isEmpty()) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT,
                    "collectionIds must contain at least one non-empty id");
        }
        String id0 = ids.get(0);
        if (id0 == null || id0.isBlank()) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT,
                    "First collectionId must be non-empty");
        }
        return id0;
    }
}