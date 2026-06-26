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
package com.here.naksha.storage.http.ffw;

import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import naksha.base.StringList;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.query.IPropertyQuery;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.here.naksha.common.http.apis.ApiParamsConst.EAST;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;
import static com.here.naksha.common.http.apis.ApiParamsConst.LIMIT;
import static com.here.naksha.common.http.apis.ApiParamsConst.MARGIN;
import static com.here.naksha.common.http.apis.ApiParamsConst.NORTH;
import static com.here.naksha.common.http.apis.ApiParamsConst.SHORT_FEATURE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.SOUTH;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_TYPE;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_TYPE_QUADKEY;
import static com.here.naksha.common.http.apis.ApiParamsConst.WEST;
import static com.here.naksha.storage.http.PrepareResult.prepareResult;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class FfwInterfaceReadExecute {

  private static final Logger log = LoggerFactory.getLogger(FfwInterfaceReadExecute.class);
  private static final String HDR_STREAM_ID = "Stream-Id";

  @NotNull
  public static Response execute(@NotNull NakshaContext context, ReadFeaturesProxyWrapper request, RequestSender sender) {
    switch (request.getReadRequestType()) {
      case GET_BY_ID:
        return executeFeatureById(context, request, sender);
      case GET_BY_IDS:
        return executeFeaturesById(context, request, sender);
      case GET_BY_BBOX:
        return executeFeatureByBBox(context, request, sender);
      case GET_BY_TILE:
        return executeFeaturesByTile(context, request, sender);
      case ITERATE:
        return executeIterate(context, request, sender);
      default:
        throw new IllegalStateException("Unsupported read request type: " + request.getReadRequestType());
    }
  }

  private static Response executeFeatureById(
      @NotNull NakshaContext context, ReadFeaturesProxyWrapper readRequest, RequestSender requestSender) {
    String featureId = readRequest.getQueryParameter(FEATURE_ID);

    HttpResponse<byte[]> response = requestSender.sendRequest(
        format("/%s/features/%s", baseEndpoint(readRequest), featureId),
        Map.of(HDR_STREAM_ID, context.getStreamId()));

    if (response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
      // For Error 404 (not found) on single feature GetById request, we need to return empty result
      return new SuccessResponse(Collections.emptyList());
    }
    return prepareResult(response,  PrepareResult.singleFeatureMapper);
  }

  private static Response executeFeaturesById(
      @NotNull NakshaContext context, ReadFeaturesProxyWrapper readRequest, RequestSender requestSender) {
    List<String> featureIds = readRequest.getQueryParameter(FEATURE_IDS);
    String queryParamsString = FEATURE_IDS + "=" + String.join(",", featureIds);

    HttpResponse<byte[]> response = requestSender.sendRequest(
        format("/%s/features?%s", baseEndpoint(readRequest), queryParamsString),
        Map.of(HDR_STREAM_ID, context.getStreamId()));

    return prepareResult(response,  PrepareResult.collectionMapper);
  }

  private static Response executeFeatureByBBox(
      @NotNull NakshaContext context, ReadFeaturesProxyWrapper readRequest, RequestSender requestSender) {
    String queryParamsString = keysToKeyValuesStrings(readRequest, WEST, NORTH, EAST, SOUTH, LIMIT);
    String featureIdsQueryString = getFeatureIdsQueryOrEmpty(readRequest);
    String propertyQueryString = getPOpQueryOrEmpty(readRequest);

    HttpResponse<byte[]> response = requestSender.sendRequest(
        format("/%s/bbox?%s%s%s", baseEndpoint(readRequest), queryParamsString, featureIdsQueryString, propertyQueryString),
        Map.of(HDR_STREAM_ID, context.getStreamId()));

    return prepareResult(response,  PrepareResult.collectionMapper);
  }

  private static String getFeatureIdsQueryOrEmpty(ReadFeaturesProxyWrapper readRequest) {
    StringList featureIds = readRequest.getFeatureIds();
    if (featureIds.isEmpty()) {
      return "";
    } else {
      return featureIds.stream().collect(joining(
          ",", // delimeter
          String.format("&%s=", SHORT_FEATURE_ID), // prefix
          "" // suffix
      ));
    }
  }

  private static Response executeFeaturesByTile(
      @NotNull NakshaContext context, ReadFeaturesProxyWrapper readRequest, RequestSender requestSender) {
    String tileType = readRequest.getQueryParameter(TILE_TYPE);
    if (tileType != null && !tileType.equals(TILE_TYPE_QUADKEY)) {
      return new ErrorResponse(new NakshaError(NakshaError.NOT_IMPLEMENTED, "Tile type other than " + TILE_TYPE_QUADKEY));
    }

    String queryParamsString = keysToKeyValuesStrings(readRequest, MARGIN, LIMIT);
    String featureIdsQueryString = getFeatureIdsQueryOrEmpty(readRequest);
    String tileId = readRequest.getQueryParameter(TILE_ID);
    HttpResponse<byte[]> response = requestSender.sendRequest(
        format(
            "/%s/quadkey/%s?%s%s%s",
            baseEndpoint(readRequest), tileId, queryParamsString, featureIdsQueryString, getPOpQueryOrEmpty(readRequest)),
        Map.of(HDR_STREAM_ID, context.getStreamId()));

    return prepareResult(response, PrepareResult.collectionMapper);
  }

  private static Response executeIterate(
      @NotNull NakshaContext context, ReadFeaturesProxyWrapper readRequest, RequestSender requestSender) {
    String queryParamsString = keysToKeyValuesStrings(readRequest, LIMIT);

    HttpResponse<byte[]> response = requestSender.sendRequest(
        format("/%s/iterate?%s", baseEndpoint(readRequest), queryParamsString),
        Map.of(HDR_STREAM_ID, context.getStreamId()));

    return prepareResult(response,  PrepareResult.collectionMapper);
  }

  /**
   * @return either POp query string starting with "&" or an empty string if the POp is null
   */
  private static String getPOpQueryOrEmpty(ReadFeaturesProxyWrapper readRequest) {
    final IPropertyQuery propertyQuery = readRequest.getQuery().getProperties();
    return propertyQuery == null ? "" : "&" + IPropertyQueryToQueryConverter.convert(propertyQuery);
  }

  /**
   * Only for keys with string values
   */
  private static String keysToKeyValuesStrings(ReadFeaturesProxyWrapper readRequest, String... key) {
    return Arrays.stream(key)
        .map(k -> k + "=" + readRequest.getQueryParameter(k))
        .collect(joining("&"));
  }

  private static String baseEndpoint(ReadFeaturesProxyWrapper request) {
    return request.getCollectionId();
  }
}
