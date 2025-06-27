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
import com.here.naksha.lib.core.models.indexing.ConstraintMap;
import com.here.naksha.lib.core.models.indexing.Index;
import java.util.List;

import com.here.naksha.lib.core.models.indexing.IndexMap;
import naksha.base.StringList;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.NakshaError.ILLEGAL_STATE;
import static naksha.base.NakshaError.raise;
import static naksha.base.Platform.forClass;

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
    var name = getAs(NAME, String_TYPE);
    if (name == null) raise(ILLEGAL_STATE, "name is no string");
    assert name != null;
    return name;
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
    final NakshaProperties properties = super.getProperties();
    if (properties instanceof SpaceProperties) {
      return (SpaceProperties) properties;
    }
    return forClass(SpaceProperties.class).proxy(properties);
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
  public @Nullable List<@Nullable Copyright> getCopyright() {
    return getAs(COPYRIGHT, forClass(Copyright.List.class));
  }

  public void setCopyright(final @Nullable List<@Nullable Copyright> copyright) {
    final Copyright.List proxyBasedCopyright = new Copyright.List();
    if (copyright != null) proxyBasedCopyright.addAll(copyright);
    set(COPYRIGHT, proxyBasedCopyright);
  }

  public @NotNull Space withCopyright(final List<Copyright> copyright) {
    setCopyright(copyright);
    return this;
  }

  /**
   * Information about the license bound to the data within the space. For valid keywords see {@link License}.
   */
  public @Nullable License getLicense() {
    return getAs(LICENSE, forClass(License.class));
  }

  public void setLicense(final @Nullable License license) {
    set(LICENSE, license);
  }

  public @NotNull Space withLicense(final License license) {
    setLicense(license);
    return this;
  }

  /**
   * List of packages that this space belongs to.
   */
  public @Nullable List<@NotNull String> getPackages() {
    return getAs(PACKAGES, forClass(StringList.class));
  }

  public void setPackages(final @Nullable List<@NotNull String> packages) {
    if (packages != null) {
      setPackages(StringList.fromList(packages));
    }
  }

  public void setPackages(final @Nullable StringList packages) {
    set(PACKAGES, packages);
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
  public @Nullable IndexMap getIndices() {
    return getAs(INDICES, forClass(IndexMap.class));
  }

  public void setIndices(@Nullable java.util.Map<@NotNull String, @NotNull Index> indices) {
    IndexMap proxyBasedIndices = new IndexMap();
    if (indices != null) proxyBasedIndices.putAll(indices);
    set(INDICES, proxyBasedIndices);
  }

  /**
   * A map defined by the user to apply constraints on feature-properties to prevent illegal values. Note that creating constraints later
   * will fail, if the space does not fulfill the constraint.
   */
  public @Nullable ConstraintMap getConstraints() {
    return getAs(CONSTRAINTS, ConstraintMap.TYPE);
  }

  public void setConstraints(@Nullable java.util.Map<@NotNull String, @NotNull Constraint> constraints) {
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
      return collection.getId();
    }
    return getId();
  }
}
