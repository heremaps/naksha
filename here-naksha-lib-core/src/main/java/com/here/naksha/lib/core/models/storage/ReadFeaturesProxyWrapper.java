/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.payload.events.QueryParameter;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReadFeaturesProxyWrapper extends ReadFeatures {

  private final ReadFeatures readFeatures;
  private final GetBy getBy;
  private final Map<String, QueryParameter> queryParameters;

  public enum GetBy {
    ID,
    IDS,
    BBOX,
    TILE
  }

  public ReadFeaturesProxyWrapper(
      ReadFeatures readFeatures, GetBy getBy, Map<String, QueryParameter> queryParameters) {
    this.readFeatures = readFeatures;
    this.getBy = getBy;
    this.queryParameters = queryParameters;
  }

  public GetBy getGetBy() {
    return getBy;
  }

  public Map<String, QueryParameter> getQueryParameters() {
    return queryParameters;
  }

  // Proxy methods
  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  @JsonProperty
  @NotNull
  public ReadFeatures withReturnDeleted(boolean returnDeleted) {
    return readFeatures.withReturnDeleted(returnDeleted);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  @NotNull
  public ReadFeatures withReturnAllVersions(boolean returnAllVersions) {
    return readFeatures.withReturnAllVersions(returnAllVersions);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public boolean isReturnAllVersions() {
    return readFeatures.isReturnAllVersions();
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull List<@NotNull String> getCollections() {
    return readFeatures.getCollections();
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public void setCollections(@NotNull List<@NotNull String> collections) {
    readFeatures.setCollections(collections);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  public ReadFeatures withCollections(@NotNull List<@NotNull String> collections) {
    return readFeatures.withCollections(collections);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  public ReadFeatures addCollection(@NotNull String collectionId) {
    return readFeatures.addCollection(collectionId);
  }

  @Override
  @Nullable
  public SOp getSpatialOp() {
    return readFeatures.getSpatialOp();
  }

  @Override
  @Nullable
  public SOp setSpatialOp(@Nullable SOp spatialOp) {
    return readFeatures.setSpatialOp(spatialOp);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  public ReadFeatures withSpatialOp(@Nullable SOp spatialOp) {
    return readFeatures.withSpatialOp(spatialOp);
  }

  @Override
  @Nullable
  public POp getPropertyOp() {
    return readFeatures.getPropertyOp();
  }

  @Override
  @Nullable
  public POp setPropertyOp(@Nullable POp propertyOp) {
    return readFeatures.setPropertyOp(propertyOp);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  public ReadFeatures withPropertyOp(@Nullable POp propertyOp) {
    return readFeatures.withPropertyOp(propertyOp);
  }

  @Override
  public ReadFeatures shallowClone() {
    return new ReadFeaturesProxyWrapper(super.shallowClone(), this.getBy, queryParameters);
  }
}
