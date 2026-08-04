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
import static com.here.naksha.app.service.http.apis.ApiParams.extractParamAsStringList;
import static com.here.naksha.app.service.http.apis.ApiParams.queryParamsFromRequest;
import static com.here.naksha.app.service.http.tasks.processor.Mom10PostProcessor.MOM_10_POST_PROCESSOR;
import static com.here.naksha.app.service.http.tasks.processor.SequentialPostProcessor.combine;
import static com.here.naksha.common.http.apis.ApiParamsConst.CLIP_GEO;
import static com.here.naksha.common.http.apis.ApiParamsConst.DEF_FEATURE_LIMIT;
import static com.here.naksha.common.http.apis.ApiParamsConst.EAST;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;
import static com.here.naksha.common.http.apis.ApiParamsConst.HANDLE;
import static com.here.naksha.common.http.apis.ApiParamsConst.LAT;
import static com.here.naksha.common.http.apis.ApiParamsConst.LIMIT;
import static com.here.naksha.common.http.apis.ApiParamsConst.LON;
import static com.here.naksha.common.http.apis.ApiParamsConst.MARGIN;
import static com.here.naksha.common.http.apis.ApiParamsConst.NORTH;
import static com.here.naksha.common.http.apis.ApiParamsConst.NULL_COORDINATE;
import static com.here.naksha.common.http.apis.ApiParamsConst.PROPERTY_SEARCH_OP;
import static com.here.naksha.common.http.apis.ApiParamsConst.RADIUS;
import static com.here.naksha.common.http.apis.ApiParamsConst.REF_FEATURE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.REF_SPACE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.SOUTH;
import static com.here.naksha.common.http.apis.ApiParamsConst.SPACE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_ID;
import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_TYPE;
import static com.here.naksha.common.http.apis.ApiParamsConst.WEST;
import static com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper.proxyWrapperOf;
import static naksha.base.FeatureType.COLLECTION;
import static naksha.base.FeatureType.FEATURE;
import static naksha.model.util.RequestHelper.readFeaturesByIdRequest;
import static naksha.model.util.RequestHelper.readFeaturesByIdsRequest;

import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.apis.ApiParams;
import com.here.naksha.app.service.http.ops.FeatureIdQueryUtil;
import com.here.naksha.app.service.http.ops.PropertyQueryUtil;
import com.here.naksha.app.service.http.ops.PropertySelectionUtil;
import com.here.naksha.app.service.http.ops.TagQueryUtil;
import com.here.naksha.app.service.http.ops.TileToBboxUtil;
import com.here.naksha.app.service.http.tasks.processor.FeaturePostProcessor;
import com.here.naksha.app.service.http.tasks.processor.GeoClipPostProcessor;
import com.here.naksha.app.service.http.tasks.processor.PropertySelectionPostProcessor;
import com.here.naksha.app.service.models.IterateHandle;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.payload.events.QueryParameterList;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper.ReadRequestType;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naksha.base.Id;
import naksha.base.StringList;
import naksha.geo.PointCoord;
import naksha.geo.SpBoundingBox;
import naksha.geo.SpGeometry;
import naksha.geo.SpPoint;
import naksha.geo.SpPolygon;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.RequestQuery;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.ISpatialQuery;
import naksha.model.request.query.ITagQuery;
import naksha.model.request.query.SpBuffer;
import naksha.model.request.query.SpIntersects;
import naksha.model.util.ResultHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadFeatureApiTask<T extends XyzResponse> extends AbstractApiTask<XyzResponse> {

  private static final Logger logger = LoggerFactory.getLogger(ReadFeatureApiTask.class);
  private final @NotNull ReadFeatureApiReqType reqType;

  public enum ReadFeatureApiReqType {
    GET_BY_ID,
    GET_BY_IDS,
    GET_BY_BBOX,
    GET_BY_TILE,
    SEARCH,
    ITERATE,
    GET_BY_RADIUS,
    GET_BY_RADIUS_POST
  }

  public ReadFeatureApiTask(
      final @NotNull ReadFeatureApiReqType reqType,
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
    try {
      return switch (this.reqType) {
        case GET_BY_ID -> executeFeatureById();
        case GET_BY_IDS -> executeFeaturesById();
        case GET_BY_BBOX -> executeFeaturesByBBox();
        case GET_BY_TILE -> executeFeaturesByTile();
        case SEARCH -> executeSearch();
        case ITERATE -> executeIterate();
        case GET_BY_RADIUS -> executeFeaturesByRadius();
        case GET_BY_RADIUS_POST -> executeFeaturesByRadiusPost();
        default -> executeUnsupported();
      };
    } catch (NakshaException nakshaException) {
      logger.warn("Known exception while processing request. ", nakshaException);
      return verticle.sendErrorResponse(routingContext, nakshaException.getError());
    } catch (Exception unknownException) {
      logger.error("Unexpected error while processing request. ", unknownException);
      return verticle.sendErrorResponse(
          routingContext, NakshaError.EXCEPTION, "Internal error : " + unknownException.getMessage());
    }
  }

  private @NotNull XyzResponse executeFeaturesById() {
    // Parse parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final QueryParameterList queryParameters = queryParamsFromRequest(routingContext);
    final List<String> featureIds = extractParamAsStringList(queryParameters, FEATURE_IDS);
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParameters);

    // Validate parameters
    if (featureIds == null || featureIds.isEmpty()) {
      return verticle.sendErrorResponse(routingContext, NakshaError.ILLEGAL_ARGUMENT, "Missing id parameter");
    }
    final ReadFeaturesProxyWrapper rdRequest = proxyWrapperOf(readFeaturesByIdsRequest(null, spaceId, featureIds))
        .withReadRequestType(ReadRequestType.GET_BY_IDS)
        .addQueryParameter(FEATURE_IDS, featureIds);

    // Forward request to NH Space Storage reader instance
    Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http FeatureCollection response
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, postProcessor(propPaths));
  }

  private @NotNull XyzResponse executeFeatureById() {
    // Parse and validate Path parameters
    final Id spaceId = COLLECTION.id(extractMandatoryPathParam(routingContext, SPACE_ID));
    final Id featureId = FEATURE.id(extractMandatoryPathParam(routingContext, FEATURE_ID));
    final QueryParameterList queryParameters = queryParamsFromRequest(routingContext);
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParameters);

    final ReadFeatures rdRequest = proxyWrapperOf(readFeaturesByIdRequest(null, spaceId, featureId))
        .withReadRequestType(ReadRequestType.GET_BY_ID)
        .addQueryParameter(FEATURE_ID, featureId);

    // Forward request to NH Space Storage reader instance
    Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http XyzFeature response
    return transformResponseToXyzFeatureResponse(response, NakshaFeature.class, NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS,
        postProcessor(propPaths));
  }

  private @NotNull XyzResponse executeFeaturesByBBox() {
    // Parse and validate Path parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    // Parse and validate Query parameters
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    if (queryParams == null || queryParams.size() <= 0) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, "Missing mandatory parameters");
    }
    final double west = ApiParams.extractQueryParamAsDouble(queryParams, WEST, true);
    final double north = ApiParams.extractQueryParamAsDouble(queryParams, NORTH, true);
    final double east = ApiParams.extractQueryParamAsDouble(queryParams, EAST, true);
    final double south = ApiParams.extractQueryParamAsDouble(queryParams, SOUTH, true);
    int limit = ApiParams.extractQueryParamAsInt(queryParams, LIMIT, false, DEF_FEATURE_LIMIT);
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParams);
    final boolean clip = ApiParams.extractQueryParamAsBoolean(queryParams, CLIP_GEO, false);
    // validate values
    limit = (limit < 0 || limit > DEF_FEATURE_LIMIT) ? DEF_FEATURE_LIMIT : limit;
    ApiParams.validateParamRange(WEST, west, -180, 180);
    ApiParams.validateParamRange(NORTH, north, -90, 90);
    ApiParams.validateParamRange(EAST, east, -180, 180);
    ApiParams.validateParamRange(SOUTH, south, -90, 90);

    // Prepare read request based on parameters supplied
    final SpBoundingBox bbox = new SpBoundingBox(west, south, east, north);
    final ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(queryParams);
    final IPropertyQuery propertyQuery = PropertyQueryUtil.propertyQueryFromParams(queryParams);
    final StringList suppliedFeatureIds = FeatureIdQueryUtil.featureIdsFromParams(queryParams);

    final Map<String, Object> queryParamsMap = new HashMap<>();
    queryParamsMap.put(WEST, west);
    queryParamsMap.put(NORTH, north);
    queryParamsMap.put(EAST, east);
    queryParamsMap.put(SOUTH, south);
    queryParamsMap.put(LIMIT, limit);
    queryParamsMap.put(CLIP_GEO, clip);
    if (propertyQuery != null) {
      queryParamsMap.put(PROPERTY_SEARCH_OP, propertyQuery);
    }

    final ReadFeatures readFeatures = new ReadFeaturesProxyWrapper()
        .withFeatureIds(suppliedFeatureIds)
        .withReadRequestType(ReadRequestType.GET_BY_BBOX)
        .withQueryParameters(queryParamsMap)
        .withLimit(limit)
        .withCollection(spaceId)
        .withSpatialQuery(new SpIntersects(bbox.toPolygon()))
        .withTagsQuery(tagQuery)
        .withPropertyQuery(propertyQuery);

    // Forward request to NH Space Storage reader instance
    final Response response = executeReadRequestFromSpaceStorage(readFeatures);

    // transform Result to Http FeatureCollection response, restricted by given feature limit
    // we will also apply feature postprocessing (like property selection and geometry clipping)
    // if any of the options is enabled
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, 0, limit, null,
        postProcessor(propPaths, clip, bbox.toPolygon()));
  }

  private @NotNull XyzResponse executeFeaturesByTile() {
    // Parse and validate Path parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);
    final String tileType = ApiParams.extractMandatoryPathParam(routingContext, TILE_TYPE);
    final String tileId = ApiParams.extractMandatoryPathParam(routingContext, TILE_ID);

    // Parse and validate Query parameters
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    // NOTE : queryParams can be null, but that is acceptable. We will move on with default values.
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParams);
    final boolean clip = ApiParams.extractQueryParamAsBoolean(queryParams, CLIP_GEO, false);
    final long margin = ApiParams.extractQueryParamAsLong(queryParams, MARGIN, false);
    ApiParams.validateParamRange(MARGIN, margin, 0, Integer.MAX_VALUE);
    int limit = ApiParams.extractQueryParamAsInt(queryParams, LIMIT, false, DEF_FEATURE_LIMIT);
    // validate values
    limit = (limit < 0 || limit > DEF_FEATURE_LIMIT) ? DEF_FEATURE_LIMIT : limit;

    // Prepare read request based on parameters supplied
    final SpPolygon tilePolygon = TileToBboxUtil.bboxPolygonForTile(tileType, tileId, (int) margin);
    final ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(queryParams);
    final IPropertyQuery propertyQuery = PropertyQueryUtil.propertyQueryFromParams(queryParams);
    final StringList suppliedFeatureIds = FeatureIdQueryUtil.featureIdsFromParams(queryParams);

    final Map<String, Object> queryParamsMap = new HashMap<>();
    queryParamsMap.put(MARGIN, margin);
    queryParamsMap.put(LIMIT, limit);
    queryParamsMap.put(TILE_TYPE, tileType);
    queryParamsMap.put(TILE_ID, tileId);
    queryParamsMap.put(CLIP_GEO, clip);
    if (propertyQuery != null) {
      queryParamsMap.put(PROPERTY_SEARCH_OP, propertyQuery);
    }

    final ReadFeatures rdRequest = new ReadFeaturesProxyWrapper()
        .withFeatureIds(suppliedFeatureIds)
        .withReadRequestType(ReadRequestType.GET_BY_TILE)
        .withQueryParameters(queryParamsMap)
        .withLimit(limit)
        .withCollection(spaceId)
        .withSpatialQuery(new SpIntersects(tilePolygon))
        .withTagsQuery(tagQuery)
        .withPropertyQuery(propertyQuery);

    // Forward request to NH Space Storage reader instance
    final Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http FeatureCollection response, restricted by given feature limit
    // we will also apply feature postprocessing (like property selection and geometry clipping)
    // if any of the options is enabled
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, 0, limit, null,
        postProcessor(propPaths, clip, tilePolygon));
  }

  private @NotNull XyzResponse executeSearch() {
    // Parse and validate Path parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    // Parse and validate Query parameters
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    if (queryParams == null || queryParams.size() <= 0) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, "Missing mandatory query parameters");
    }
    int limit = ApiParams.extractQueryParamAsInt(queryParams, LIMIT, false, DEF_FEATURE_LIMIT);
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParams);
    // validate values
    limit = (limit < 0 || limit > DEF_FEATURE_LIMIT) ? DEF_FEATURE_LIMIT : limit;

    // Prepare read request based on parameters supplied
    final ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(queryParams);
    final IPropertyQuery propertyQuery = PropertyQueryUtil.propertyQueryFromParams(queryParams);
    if (tagQuery == null && propertyQuery == null) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, "None of Tags or Prop search parameters is present, at least one is required.");
    }
    final StringList suppliedFeatureIds = FeatureIdQueryUtil.featureIdsFromParams(queryParams);
    final ReadFeatures rdRequest = new ReadFeatures()
        .withPropertyQuery(propertyQuery)
        .withTagQuery(tagQuery);
    rdRequest.setFeatureIds(suppliedFeatureIds);
    rdRequest.setCollectionId(spaceId);
    rdRequest.setLimit(limit);

    // Forward request to NH Space Storage reader instance
    final Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http FeatureCollection response, restricted by given feature limit
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, 0, limit, null, postProcessor(propPaths));
  }

  private @NotNull XyzResponse executeIterate() {
    // Parse and validate Path parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    // Parse and validate Query parameters
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);

    // Parse property selection
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParams);

    // Note : subsequent steps need to support queryParams being null

    // extract limit parameter
    int clientLimit = ApiParams.extractQueryParamAsInt(queryParams, LIMIT, false, DEF_FEATURE_LIMIT);
    // extract handle parameter
    IterateHandle handle = ApiParams.extractQueryParamAsIterateHandle(queryParams, HANDLE);
    // create new "handle" if not already provided, or overwrite parameters based on "handle"
    if (handle == null) {
      handle = new IterateHandle().withLimit(clientLimit);
    }
    int offset = handle.getOffset();
    clientLimit = handle.getLimit();
    clientLimit = (clientLimit < 0 || clientLimit > DEF_FEATURE_LIMIT) ? DEF_FEATURE_LIMIT : clientLimit;
    final Map<String, Object> queryParamsMap = Map.of(LIMIT, clientLimit);

    // Prepare read request based on parameters supplied
    final ReadFeatures rdRequest = new ReadFeaturesProxyWrapper()
        .withReadRequestType(ReadRequestType.ITERATE)
        .withQueryParameters(queryParamsMap)
        .withLimit(clientLimit + offset)
        .withCollection(spaceId);

    // Forward request to NH Space Storage reader instance
    final Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http FeatureCollection response,
    // restricted by given feature limit and by adding "handle" attribute to support subsequent iteration
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, offset, clientLimit, handle, postProcessor(propPaths));
  }

  // TODO: refactor: this and other radius method are basically the sma,e only refGeo origin differs
  private @NotNull XyzResponse executeFeaturesByRadius() {
    // Parse and validate Path parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    // Parse and validate Query parameters
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    if (queryParams == null || queryParams.size() <= 0) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, "Missing mandatory parameters");
    }
    final double lat = ApiParams.extractQueryParamAsDouble(queryParams, LAT, false, NULL_COORDINATE);
    final double lon = ApiParams.extractQueryParamAsDouble(queryParams, LON, false, NULL_COORDINATE);
    final String refSpaceId = ApiParams.extractParamAsString(queryParams, REF_SPACE_ID);
    final String refFeatureId = ApiParams.extractParamAsString(queryParams, REF_FEATURE_ID);
    final long radius = ApiParams.extractQueryParamAsLong(queryParams, RADIUS, false, 0);
    int limit = ApiParams.extractQueryParamAsInt(queryParams, LIMIT, false, DEF_FEATURE_LIMIT);
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParams);
    // validate values
    limit = (limit < 0 || limit > DEF_FEATURE_LIMIT) ? DEF_FEATURE_LIMIT : limit;
    ApiParams.validateLatLon(lat, lon);
    ApiParams.validateParamRange(RADIUS, radius, 0, Long.MAX_VALUE);

    // Obtain reference geometry based on given coordinates or feature reference
    final SpGeometry refGeometry = obtainReferenceGeometry(lat, lon, refSpaceId, refFeatureId);

    // Prepare read request based on parameters supplied
    final ISpatialQuery radiusQuery =
        (radius > 0) ? new SpIntersects(refGeometry, new SpBuffer(radius, true)) : new SpIntersects(refGeometry);
    final ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(queryParams);
    final IPropertyQuery propertyQuery = PropertyQueryUtil.propertyQueryFromParams(queryParams);
    final StringList suppliedFeatureIds = FeatureIdQueryUtil.featureIdsFromParams(queryParams);
    final RequestQuery query = new RequestQuery();
    query.setSpatial(radiusQuery);
    query.setTags(tagQuery);
    final ReadFeatures rdRequest = new ReadFeatures();
    rdRequest.setFeatureIds(suppliedFeatureIds);
    rdRequest.setCollectionId(spaceId);
    rdRequest.setQuery(query);
    rdRequest.withPropertyQuery(propertyQuery);

    // Forward request to NH Space Storage reader instance
    final Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http FeatureCollection response, restricted by given feature limit
    FeaturePostProcessor<NakshaFeature> postProcessor = postProcessor(propPaths); // TODO CASL-1479: consider adding clip support
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, 0, limit, null, postProcessor);
  }

  private @NotNull SpGeometry obtainReferenceGeometry(
      final double lat,
      final double lon,
      final @Nullable String refSpaceId,
      final @Nullable String refFeatureId) {
    // if both lan and lon provided, then prepare Point geometry
    if (lat != NULL_COORDINATE && lon != NULL_COORDINATE) {
      return new SpPoint(new PointCoord(lon, lat));
    }

    // Validate that both refSpaceId and refFeatureId provided and not just one
    if (refSpaceId == null || refSpaceId.isEmpty()) {
      throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Missing %s param".formatted(REF_SPACE_ID));
    } else if (refFeatureId == null || refFeatureId.isEmpty()) {
      throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Missing %s param".formatted(REF_FEATURE_ID));
    }

    // Find geometry by querying referenced feature
    NakshaFeature feature = null;
    // Forward Read request to NHSpaceStorage instance
    final ReadFeatures rdRequest = proxyWrapperOf(readFeaturesByIdRequest(null, refSpaceId, refFeatureId))
        .withReadRequestType(ReadRequestType.GET_BY_ID)
        .addQueryParameter(FEATURE_ID, refFeatureId);
    final Response response = executeReadRequestFromSpaceStorage(rdRequest);
    if (response instanceof SuccessResponse successResponse) {
      feature = ResultHelper.readFeatureFromResponse(successResponse, NakshaFeature.class);
    } else if (response instanceof ErrorResponse errorResponse) {
      throw new NakshaException(errorResponse.getError());
    } else {
      throw new NakshaException(NakshaError.EXCEPTION, "Unexpected result while retrieving referenced feature");
    }
    if (feature == null) {
      throw new NakshaException(
          NakshaError.NOT_FOUND,
          "No feature found for given spaceId %s and featureId %s".formatted(refSpaceId, refFeatureId));
    } else if (feature.getGeometry() == null) {
      throw new NakshaException(NakshaError.NOT_FOUND, "Missing geometry for referenced feature");
    }

    return feature.getGeometry();
  }

  private @NotNull XyzResponse executeFeaturesByRadiusPost() {
    // Parse and validate Path parameters
    final String spaceId = ApiParams.extractMandatoryPathParam(routingContext, SPACE_ID);

    // Parse and validate Query parameters
    final QueryParameterList queryParams = queryParamsFromRequest(routingContext);
    // NOTE : queryParams can be null. Subsequent steps should respect the same.
    final long radius = ApiParams.extractQueryParamAsLong(queryParams, RADIUS, false, 0);
    int limit = ApiParams.extractQueryParamAsInt(queryParams, LIMIT, false, DEF_FEATURE_LIMIT);
    final Set<String> propPaths = PropertySelectionUtil.buildPropPathSetFromQueryParams(queryParams);
    // validate values
    limit = (limit < 0 || limit > DEF_FEATURE_LIMIT) ? DEF_FEATURE_LIMIT : limit;
    ApiParams.validateParamRange(RADIUS, radius, 0, Long.MAX_VALUE);

    // Obtain reference geometry based on given coordinates or feature reference
    final SpGeometry refGeometry = parseRequestBodyAs(SpGeometry.class);

    // Prepare read request based on parameters supplied
    final ISpatialQuery radiusQuery =
        (radius > 0) ? new SpIntersects(refGeometry, new SpBuffer(radius, true)) : new SpIntersects(refGeometry);
    final ITagQuery tagQuery = TagQueryUtil.tagQueryFromParams(queryParams);
    final IPropertyQuery propertyQuery = PropertyQueryUtil.propertyQueryFromParams(queryParams);
    final StringList suppliedFeatureIds = FeatureIdQueryUtil.featureIdsFromParams(queryParams);
    final RequestQuery query = new RequestQuery();
    query.setSpatial(radiusQuery);
    query.setTags(tagQuery);
    final ReadFeatures rdRequest = new ReadFeatures();
    rdRequest.setCollectionId(spaceId);
    rdRequest.setFeatureIds(suppliedFeatureIds);
    rdRequest.setQuery(query);
    rdRequest.withPropertyQuery(propertyQuery);

    // Forward request to NH Space Storage reader instance
    final Response response = executeReadRequestFromSpaceStorage(rdRequest);
    // transform Result to Http FeatureCollection response, restricted by given feature limit
    FeaturePostProcessor<NakshaFeature> postProcessor = postProcessor(propPaths); // TODO CASL-1479: consider adding clip support
    return transformResponseToXyzCollectionResponse(response, NakshaFeature.class, 0, limit, null, postProcessor);
  }

  private @NotNull FeaturePostProcessor<NakshaFeature> postProcessor(Set<String> propPaths) {
    if (propPaths == null || propPaths.isEmpty()) {
      return MOM_10_POST_PROCESSOR;
    }
    return combine(MOM_10_POST_PROCESSOR, new PropertySelectionPostProcessor(propPaths));
  }

  private @NotNull FeaturePostProcessor<NakshaFeature> postProcessor(
      Set<String> propPaths, boolean clip, SpGeometry clipGeo) {
    if (propPaths != null && !propPaths.isEmpty()) {
      PropertySelectionPostProcessor propSelectionPostProcessor = new PropertySelectionPostProcessor(propPaths);
      if (clip) {
        return combine(MOM_10_POST_PROCESSOR, propSelectionPostProcessor, new GeoClipPostProcessor(clipGeo));
      } else {
        return combine(MOM_10_POST_PROCESSOR, propSelectionPostProcessor);
      }
    } else if (clip) {
      return combine(MOM_10_POST_PROCESSOR, new GeoClipPostProcessor(clipGeo));
    } else {
      return MOM_10_POST_PROCESSOR;
    }
  }
}
