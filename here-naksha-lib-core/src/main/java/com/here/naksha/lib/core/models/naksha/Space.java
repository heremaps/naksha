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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.Copyright;
import com.here.naksha.lib.core.models.License;
import com.here.naksha.lib.core.models.Typed;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.indexing.Constraint;
import com.here.naksha.lib.core.models.indexing.Index;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The space configuration. A space is an event-pipeline accessible via the REST API.
 */
@SuppressWarnings("unused")
@JsonTypeName(value = "Space")
public final class Space extends EventTarget<Space> implements Typed {

  /**
   * Create new space initialized with the given identifier.
   *
   * @param id            the identifier.
   * @param eventHandlers the list of event handler identifiers to form the event-pipeline.
   */
  @JsonCreator
  public Space(
      @JsonProperty(ID) @NotNull String id,
      @JsonProperty(EVENT_HANDLER_IDS) @NotNull List<@NotNull String> eventHandlers) {
    super(id, eventHandlers);
  }

  /**
   * Create new space with the given identifier.
   *
   * @param id the identifier of the space.
   */
  public Space(@NotNull String id) {
    super(id);
  }

  /**
   * Beta release date: 2018-10-01T00:00Z[UTC]
   */
  private final long DEFAULT_TIMESTAMP = 1538352000000L;

  /**
   * The catalog identifier.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  @JsonProperty
  private @Nullable String catalogId;

  /**
   * The name of the space, must be unique within a given catalog.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  @JsonProperty
  private @NotNull String name;

  /**
   * If set to true, every authenticated user can read the features in the space.
   */
  @JsonProperty
  @JsonInclude(Include.NON_DEFAULT)
  private boolean shared = false;

  /**
   * Copyright information for the data in the space.
   */
  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<Copyright> copyright;

  /**
   * Information about the license bound to the data within the space. For valid keywords see {@link License}.
   */
  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private License license;

  /**
   * List of packages that this space belongs to.
   */
  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<@NotNull String> packages;

  /**
   * Arbitrary properties added to the space, this includes the standard {@link XyzNamespace}.
   */
  @JsonProperty
  private XyzProperties properties;

  /**
   * Indicates if the space is in a read-only mode.
   */
  @JsonProperty
  @JsonInclude(Include.NON_DEFAULT)
  private boolean readOnly = false;

  /**
   * A map defined by the user to index feature-properties to make them searchable and sortable. The key is the name of the index to create,
   * the value describes the properties to index including their ordering in the index. Properties not being indexes still can be searched,
   * but the result can be bad.
   */
  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private Map<@NotNull String, @NotNull Index> indices;

  /**
   * A map defined by the user to apply constraints on feature-properties to prevent illegal values. Note that creating constraints later
   * will fail, if the space does not fulfill the constraint.
   */
  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private Map<@NotNull String, @NotNull Constraint> constraints;

  /**
   * If set, then the owner of all features in this space forcefully set to this value.
   */
  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private String forceOwner;

  /**
   * Returns the name of the space.
   *
   * @return the name of the space.
   */
  public @NotNull String getName() {
    return name;
  }

  /**
   * Returns the collection identifier of the collection in which to persist the space; if any.
   *
   * @return the collection identifier.
   */
  @JsonIgnore
  public @NotNull String getCollectionId() {
    String collectionIdFromProps = null;
    Object collectionProps = getProperties().get(SpaceProperties.XYZ_COLLECTION);
    if (collectionProps != null) {
      collectionIdFromProps = ((Map) collectionProps).get(XyzFeature.ID).toString();
    }
    return collectionIdFromProps != null ? collectionIdFromProps : getId();
  }

  public boolean isShared() {
    return shared;
  }

  public void setShared(final boolean shared) {
    this.shared = shared;
  }

  public @NotNull Space withShared(final boolean shared) {
    setShared(shared);
    return this;
  }

  public List<Copyright> getCopyright() {
    return copyright;
  }

  public void setCopyright(final List<Copyright> copyright) {
    this.copyright = copyright;
  }

  public @NotNull Space withCopyright(final List<Copyright> copyright) {
    setCopyright(copyright);
    return this;
  }

  public License getLicense() {
    return license;
  }

  public void setLicense(final License license) {
    this.license = license;
  }

  public @NotNull Space withLicense(final License license) {
    setLicense(license);
    return this;
  }

  public List<@NotNull String> getPackages() {
    return packages;
  }

  public @NotNull List<@NotNull String> usePackages() {
    List<@NotNull String> packages = this.packages;
    if (packages == null) {
      this.packages = packages = new ArrayList<>();
    }
    return packages;
  }

  public void setPackages(final List<@NotNull String> packages) {
    this.packages = packages;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(final boolean readOnly) {
    this.readOnly = readOnly;
  }

  public @Nullable Map<@NotNull String, @NotNull Index> getIndices() {
    return indices;
  }

  public void setIndices(@Nullable Map<@NotNull String, @NotNull Index> indices) {
    this.indices = indices;
  }

  public @Nullable Map<@NotNull String, @NotNull Constraint> getConstraints() {
    return constraints;
  }

  public void setConstraints(@Nullable Map<@NotNull String, @NotNull Constraint> constraints) {
    this.constraints = constraints;
  }

  public @Nullable String getForceOwner() {
    return forceOwner;
  }

  public void setForceOwner(final String forceOwner) {
    this.forceOwner = forceOwner;
  }

  public @NotNull Space withForceOwner(final String forceOwner) {
    setForceOwner(forceOwner);
    return this;
  }
}
