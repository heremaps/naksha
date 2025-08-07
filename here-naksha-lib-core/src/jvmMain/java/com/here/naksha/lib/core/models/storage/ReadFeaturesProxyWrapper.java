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

import java.util.Map;

import naksha.base.MapProxy;
import naksha.base.PlatformType;
import naksha.base.StringList;
import naksha.model.request.ReadFeatures;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.ISpatialQuery;
import naksha.model.request.query.ITagQuery;
import org.jetbrains.annotations.NotNull;

import static naksha.base.NakshaBaseKt.Any_TYPE;
import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

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

  public static class QueryParameterMap extends MapProxy<String, Object> {
    static final PlatformType<QueryParameterMap> TYPE = forClass(QueryParameterMap.class);
    public QueryParameterMap() {
      super(String_TYPE, Any_TYPE);
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

  public QueryParameterMap getQueryParameters() {
    return getAs(QUERY_PARAMETERS, QueryParameterMap.TYPE);
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

  public ReadFeaturesProxyWrapper shallowClone() {
    return this.copy(false);
  }

  public ReadFeaturesProxyWrapper withLimit(int limit){
    setLimit(limit);
    return this;
  }

  public ReadFeaturesProxyWrapper withCollection(String collectionId){
    getCollectionIds().add(collectionId);
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
    return readFeatures.proxy(forClass(ReadFeaturesProxyWrapper.class));
  }
}
