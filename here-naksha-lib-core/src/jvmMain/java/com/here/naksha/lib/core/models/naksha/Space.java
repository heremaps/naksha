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
package com.here.naksha.lib.core.models.naksha;

import com.fasterxml.jackson.annotation.*;
import com.here.naksha.lib.core.models.Copyright;
import com.here.naksha.lib.core.models.License;
import com.here.naksha.lib.core.models.indexing.Constraint;
import com.here.naksha.lib.core.models.indexing.Constraint.ConstraintMap;
import com.here.naksha.lib.core.models.indexing.Index;
import java.util.List;
import java.util.Map;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The space configuration. A space is an event-pipeline accessible via the REST API.
 */
@SuppressWarnings("unused")
public final class Space extends EventTarget<Space> {

  /**
   * Beta release date: 2018-10-01T00:00Z[UTC]
   */
  private final long DEFAULT_TIMESTAMP = 1538352000000L;

  // property keys
  private static final String CATALOG_ID = "catalogId";
  private static final String NAME = "name";
  private static final String SHARED = "shared";
  private static final String COPYRIGHT = "copyright";
  private static final String LICENSE = "license";
  private static final String PACKAGES = "packages";
  private static final String READ_ONLY = "readOnly";
  private static final String INDICES = "indices";
  private static final String CONSTRAINTS = "constraints";
  private static final String FORCE_OWNER = "forceOwner";

  /**
   * @return The catalog identifier.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  public String getCatalogId() {
    return (String) getRaw(CATALOG_ID);
  }

  @AvailableSince(NakshaVersion.v2_0_3)
  public void setCatalogId(String catalogId) {
    setRaw(CATALOG_ID, catalogId);
  }

  /**
   * @return The name of the space
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  public @NotNull String getName() {
    return (String) getRaw(NAME);
  }

  /*
   * Set the name of the space, must be unique within a given catalog.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  public void setName(String name) {
    setRaw(NAME, name);
  }

  @NotNull
  @Override
  @AvailableSince(NakshaVersion.v2_0_3)
  public SpaceProperties getProperties() {
    return JvmBoxingUtil.box(super.getProperties(), SpaceProperties.class);
  }

  /**
   * @return Flag that if set to true, every authenticated user can read the features in the space.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  public boolean isShared() {
    return getOrSet(SHARED, false);
  }

  @AvailableSince(NakshaVersion.v2_0_3)
  public void setShared(final boolean shared) {
    setRaw(SHARED, shared);
  }

  @AvailableSince(NakshaVersion.v2_0_3)
  public @NotNull Space withShared(final boolean shared) {
    setShared(shared);
    return this;
  }

  /**
   * Copyright information for the data in the space.
   */
  public List<Copyright> getCopyright() {
    return JvmBoxingUtil.box(getPath(COPYRIGHT), Copyright.List.class);
  }

  public void setCopyright(final List<Copyright> copyright) {
    Copyright.List proxyBasedCopyright = new Copyright.List();
    proxyBasedCopyright.addAll(copyright);
    setRaw(COPYRIGHT, proxyBasedCopyright);
  }

  public @NotNull Space withCopyright(final List<Copyright> copyright) {
    setCopyright(copyright);
    return this;
  }

  /**
   * Information about the license bound to the data within the space. For valid keywords see {@link License}.
   */
  public License getLicense() {
    return JvmBoxingUtil.box(getPath(LICENSE), License.class);
  }

  public void setLicense(final License license) {
    setRaw(LICENSE, license);
  }

  public @NotNull Space withLicense(final License license) {
    setLicense(license);
    return this;
  }

  /**
   * List of packages that this space belongs to.
   */
  public List<@NotNull String> getPackages() {
    return JvmBoxingUtil.box(getPath(PACKAGES), StringList.class);
  }

  public void setPackages(final List<@NotNull String> packages) {
    setPackages(StringList.fromList(packages));
  }

  public void setPackages(final StringList packages) {
    setRaw(PACKAGES, packages);
  }

  /**
   * Indicates if the space is in a read-only mode.
   */
  public boolean isReadOnly() {
    return getOrSet(READ_ONLY, false);
  }

  public void setReadOnly(final boolean readOnly) {
    setRaw(READ_ONLY, readOnly);
  }

  /**
   * A map defined by the user to index feature-properties to make them searchable and sortable. The key is the name of the index to create,
   * the value describes the properties to index including their ordering in the index. Properties not being indexes still can be searched,
   * but the result can be bad.
   */
  public @Nullable Map<@NotNull String, @NotNull Index> getIndices() {
    return JvmBoxingUtil.box(getPath(INDICES), Index.Map.class);
  }

  public void setIndices(@Nullable Map<@NotNull String, @NotNull Index> indices) {
    Index.Map proxyBasedIndices = new Index.Map();
    proxyBasedIndices.putAll(indices);
    setRaw(INDICES, proxyBasedIndices);
  }

  /**
   * A map defined by the user to apply constraints on feature-properties to prevent illegal values. Note that creating constraints later
   * will fail, if the space does not fulfill the constraint.
   */
  public @Nullable Map<@NotNull String, @NotNull Constraint> getConstraints() {
    return JvmBoxingUtil.box(getPath(CONSTRAINTS), ConstraintMap.class);
  }

  public void setConstraints(@Nullable Map<@NotNull String, @NotNull Constraint> constraints) {
    ConstraintMap constraintMap = new ConstraintMap();
    constraintMap.putAll(constraints);
    setRaw(CONSTRAINTS, constraintMap);
  }

  /**
   * If set, then the owner of all features in this space forcefully set to this value.
   */
  public @Nullable String getForceOwner() {
    return (String) getRaw(FORCE_OWNER);
  }

  public void setForceOwner(final String forceOwner) {
    setRaw(FORCE_OWNER, forceOwner);
  }

  public @NotNull Space withForceOwner(final String forceOwner) {
    setForceOwner(forceOwner);
    return this;
  }

  /**
   * Returns the collection identifier of the collection in which to persist the space; if any.
   *
   * @return the collection identifier.
   */
  @JsonIgnore
  public @NotNull String getCollectionId() {
    NakshaCollection collection = getProperties().getCollection();
    if (collection != null) {
      return collection.getId().getText();
    }
    return getId().getText();
  }
}
