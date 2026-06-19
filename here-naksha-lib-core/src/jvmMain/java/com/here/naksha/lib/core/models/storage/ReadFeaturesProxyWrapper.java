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
package com.here.naksha.lib.core.models.storage;

import naksha.base.JvmBoxingUtil;
import naksha.base.JvmMapProxy;
import naksha.base.StringList;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.ISpatialQuery;
import naksha.model.request.query.ITagQuery;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public class ReadFeaturesProxyWrapper extends ReadFeatures {

  static final String READ_REQUEST_TYPE = "readRequestType";
  static final String QUERY_PARAMETERS = "queryParameters";

  public enum ReadRequestType {
    GET_BY_ID,
    GET_BY_IDS,
    GET_BY_BBOX,
    GET_BY_TILE,
    ITERATE
  }

  public ReadFeaturesProxyWrapper() {
    super();
  }

  public static class QueryParameterMap extends JvmMapProxy<String, Object> {
    public QueryParameterMap() {
      super(String.class, Object.class);
    }
  }

  public ReadRequestType getReadRequestType() {
    return (ReadRequestType) getRaw(READ_REQUEST_TYPE);
  }

  public ReadFeaturesProxyWrapper withReadRequestType(ReadRequestType requestType) {
    setRaw(READ_REQUEST_TYPE, requestType);
    return this;
  }

  public ReadFeaturesProxyWrapper withFeatureIds(StringList featureIds){
    setFeatureIds(featureIds);
    return this;
  }

  public Map<String, Object> getQueryParameters() {
    return JvmBoxingUtil.box(getPath(QUERY_PARAMETERS), QueryParameterMap.class);
  }

  public <T> T getQueryParameter(String key) throws ClassCastException {
    return (T) getQueryParameters().get(key);
  }

  public void setQueryParameters(Map<String, Object> parameters) {
    if (parameters == null) {
      setRaw(QUERY_PARAMETERS, null);
      return;
    }
    final QueryParameterMap proxyMap = new QueryParameterMap();
    proxyMap.putAll(parameters);
    setRaw(QUERY_PARAMETERS, proxyMap);
  }

  public ReadFeaturesProxyWrapper withQueryParameters(Map<String, Object> parameters) {
    setQueryParameters(parameters);
    return this;
  }

  public ReadFeaturesProxyWrapper addQueryParameter(String key, Object parameters) {
    Map<String, Object> params = getQueryParameters();
    if (params == null) {
      setQueryParameters(Map.of(key, parameters));
      return this;
    }
    params.put(key, parameters);
    return this;
  }

  public ReadFeaturesProxyWrapper withLimit(int limit){
    setLimit(limit);
    return this;
  }

  public ReadFeaturesProxyWrapper withCollection(String collectionId){
    getCollectionId().add(collectionId);
    return this;
  }

  public ReadFeaturesProxyWrapper withSpatialQuery(ISpatialQuery spatialQuery){
    getQuery().setSpatial(spatialQuery);
    return this;
  }

  public ReadFeaturesProxyWrapper withTagsQuery(ITagQuery tagQuery){
    getQuery().setTags(tagQuery);
    return this;
  }

  public ReadFeaturesProxyWrapper withPropertyQuery(IPropertyQuery propertyQuery){
    super.withPropertyQuery(propertyQuery);
    return this;
  }

  public static ReadFeaturesProxyWrapper proxyWrapperOf(@NotNull ReadFeatures readFeatures){
    return JvmBoxingUtil.box(readFeatures, ReadFeaturesProxyWrapper.class);
  }
}
