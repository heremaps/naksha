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
package com.here.naksha.lib.core.models.geojson.implementation;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.here.naksha.lib.core.LazyParsableFeatureList;
import com.here.naksha.lib.core.LazyParsableFeatureList.RawDeserializer;
import com.here.naksha.lib.core.LazyParsableFeatureList.RawSerializer;
import com.here.naksha.lib.core.models.geojson.coordinates.BBox;
import naksha.model.XyzResponse;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "FeatureCollection")
@JsonInclude(Include.NON_EMPTY)
@SuppressWarnings({"unused", "unchecked"})
public class XyzFeatureCollection extends XyzResponse {

  @JsonIgnore
  private final @NotNull LazyParsableFeatureList features;

  @JsonProperty
  @JsonInclude(Include.NON_NULL)
  private BBox bbox;

  @JsonProperty
  @JsonInclude(Include.NON_NULL)
  private Boolean partial;

  @JsonProperty
  @JsonInclude(Include.NON_NULL)
  @Deprecated
  private String handle;

  @JsonProperty
  @JsonInclude(Include.NON_NULL)
  private String nextPageToken;

  @JsonProperty
  @JsonInclude(Include.NON_NULL)
  private Long count;

  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<String> inserted;

  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<String> updated;

  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<String> deleted;

  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<XyzFeature> oldFeatures;

  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<XyzFeature> violations;

  @JsonProperty
  @JsonInclude(Include.NON_EMPTY)
  private List<ModificationFailure> failed;

  @JsonProperty
  @JsonInclude(Include.NON_NULL)
  private Integer version;

  public XyzFeatureCollection() {
    features = new LazyParsableFeatureList();
  }

  @SuppressWarnings("WeakerAccess")
  public void calculateAndSetBBox(boolean recalculateChildrenBoxes) throws JsonProcessingException {
    if (getFeatures().size() == 0) {
      return;
    }

    double minLon = Double.POSITIVE_INFINITY;
    double minLat = Double.POSITIVE_INFINITY;
    double maxLon = Double.NEGATIVE_INFINITY;
    double maxLat = Double.NEGATIVE_INFINITY;

    for (final XyzFeature feature : getFeatures()) {
      if (recalculateChildrenBoxes || feature.getBbox() == null) {
        feature.calculateAndSetBbox(recalculateChildrenBoxes);
      }

      BBox bbox = feature.getBbox();

      if (bbox != null) {
        if (bbox.minLon() < minLon) {
          minLon = bbox.minLon();
        }
        if (bbox.minLat() < minLat) {
          minLat = bbox.minLat();
        }
        if (bbox.maxLon() > maxLon) {
          maxLon = bbox.maxLon();
        }
        if (bbox.maxLat() > maxLat) {
          maxLat = bbox.maxLat();
        }
      }
    }

    if (minLon != Double.POSITIVE_INFINITY
        && minLat != Double.POSITIVE_INFINITY
        && maxLon != Double.NEGATIVE_INFINITY
        && maxLat != Double.NEGATIVE_INFINITY) {
      setBbox(new BBox(minLon, minLat, maxLon, maxLat));
    } else {
      setBbox(null);
    }
  }

  public BBox getBbox() {
    return bbox;
  }

  public void setBbox(BBox bbox) {
    this.bbox = bbox;
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withBbox(final BBox bbox) {
    setBbox(bbox);
    return this;
  }

  public @NotNull List<XyzFeature> getFeatures() {
    return (List<XyzFeature>) features.get();
  }

  public void setFeatures(@NotNull List<? extends XyzFeature> features) {
    this.features.set(features);
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withFeatures(final @NotNull List<? extends @NotNull XyzFeature> features) {
    setFeatures(features);
    return this;
  }

  @JsonSerialize(using = RawSerializer.class)
  @JsonProperty("features")
  public @NotNull LazyParsableFeatureList getLazyParsableFeatureList() {
    return features;
  }

  @JsonDeserialize(using = RawDeserializer.class)
  @JsonProperty("features")
  public void setLazyParsableFeatureList(Object features) {
    if (features instanceof String) {
      String string = (String) features;
      this.features.set(string);
    } else if (features instanceof List<?>) {
      List<?> list = (List<?>) features;
      this.features.set((List<XyzFeature>) list);
    }
  }

  /**
   * Returns the Space handle which is used to iterate above data.
   *
   * @return the handle.
   * @deprecated use {@link #getNextPageToken()} instead.
   */
  public @Nullable String getHandle() {
    return this.handle;
  }

  /**
   * Sets the Space handle that can be used to continue an iterate.
   *
   * @param handle the handle, if null the handle property is removed.
   * @deprecated use {@link #setNextPageToken(String)} instead.
   */
  @SuppressWarnings("WeakerAccess")
  public void setHandle(String handle) {
    this.handle = handle;
  }

  /**
   * @deprecated use {@link #withNextPageToken(String)} instead.
   */
  @SuppressWarnings("unused")
  public XyzFeatureCollection withHandle(final String handle) {
    setHandle(handle);
    return this;
  }

  /**
   * Returns the Space nextPageToken which is used to iterate above data.
   *
   * @return the nextPageToken.
   */
  public String getNextPageToken() {
    return this.nextPageToken;
  }

  /**
   * Sets the Space nextPageToken that can be used to continue an iterate.
   *
   * @param nextPageToken the nextPageToken, if null the nextPageToken property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setNextPageToken(String nextPageToken) {
    this.nextPageToken = nextPageToken;
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withNextPageToken(final String nextPageToken) {
    setNextPageToken(nextPageToken);
    return this;
  }

  /**
   * Returns true if FeatureCollection does not contain all results. Is used for tweaks.
   *
   * @return the handle.
   */
  public Boolean isPartial() {
    return this.partial;
  }

  /**
   * Set indication if FeatureCollection has all expected results or not.
   *
   * @param partial is true if FeatureCollection does not contains all data.
   */
  @SuppressWarnings("WeakerAccess")
  public void setPartial(Boolean partial) {
    this.partial = partial;
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withPartial(final Boolean partial) {
    setPartial(partial);
    return this;
  }

  /**
   * Returns the proprietary count property that is used by Space count requests to return the
   * number of features found.
   *
   * @return the amount of features that are matching the query.
   */
  public Long getCount() {
    return this.count;
  }

  /**
   * Sets the amount of features that where matching a query, without returning the features (so
   * features will be null or an empty array).
   *
   * @param count the amount of features that where matching a query, if null, then the property is
   *     removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setCount(Long count) {
    this.count = count;
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withCount(final Long count) {
    setCount(count);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully inserted.
   */
  public List<String> getInserted() {
    return this.inserted;
  }

  /**
   * Sets the list of successfully inserted feature IDs.
   *
   * @param inserted the IDs of the features that where inserted.
   */
  @SuppressWarnings("WeakerAccess")
  public void setInserted(List<String> inserted) {
    this.inserted = inserted;
  }

  /**
   * Appends the given feature ID into the list of inserted
   *
   * @param insertId the ID to be inserted into the list
   */
  public void appendInsertId(@NotNull String insertId) {
    if (this.inserted == null) {
      this.inserted = new ArrayList<>();
    }
    this.inserted.add(insertId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withInserted(List<String> inserted) {
    setInserted(inserted);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully updated.
   */
  public List<String> getUpdated() {
    return this.updated;
  }

  /**
   * Sets the list of successfully updated feature IDs.
   *
   * @param updated the IDs of the features that where updated.
   */
  @SuppressWarnings("WeakerAccess")
  public void setUpdated(List<String> updated) {
    this.updated = updated;
  }

  /**
   * Appends the given feature ID into the list of updated
   *
   * @param updateId the ID to be inserted into the list
   */
  public void appendUpdateId(@NotNull String updateId) {
    if (this.updated == null) {
      this.updated = new ArrayList<>();
    }
    this.updated.add(updateId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withUpdated(List<String> updated) {
    setUpdated(updated);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully deleted.
   */
  public List<String> getDeleted() {
    return this.deleted;
  }

  /**
   * Sets the list of successfully deleted feature IDs.
   *
   * @param deleted the IDs of the features that where deleted.
   */
  @SuppressWarnings("WeakerAccess")
  public void setDeleted(List<String> deleted) {
    this.deleted = deleted;
  }

  /**
   * Appends the given feature ID into the list of deleted
   *
   * @param deleteId the ID to be inserted into the list
   */
  public void appendDeleteId(@NotNull String deleteId) {
    if (this.deleted == null) {
      this.deleted = new ArrayList<>();
    }
    this.deleted.add(deleteId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withDeleted(List<String> deleted) {
    setDeleted(deleted);
    return this;
  }

  /**
   * @return A list of modification failures
   */
  public List<ModificationFailure> getFailed() {
    return this.failed;
  }

  @SuppressWarnings("WeakerAccess")
  public void setFailed(List<ModificationFailure> failed) {
    this.failed = failed;
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withFailed(List<ModificationFailure> failed) {
    setFailed(failed);
    return this;
  }

  /**
   * For FeatureCollection write-responses: If the history of a space is activated and this
   * FeatureCollection is a response to a modification of the space - contains the (new)
   * space-version which has just been written.
   *
   * @return The new space-version after some modification
   */
  public Integer getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public XyzFeatureCollection withVersion(int version) {
    setVersion(version);
    return this;
  }

  @SuppressWarnings("unused")
  public List<XyzFeature> getOldFeatures() {
    return oldFeatures;
  }

  @SuppressWarnings("WeakerAccess")
  public void setOldFeatures(List<XyzFeature> oldFeatures) {
    this.oldFeatures = oldFeatures;
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withOldFeatures(List<XyzFeature> oldFeatures) {
    setOldFeatures(oldFeatures);
    return this;
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withInsertedFeatures(
      final @NotNull List<? extends @NotNull XyzFeature> insertedFeatures) {
    ((List<XyzFeature>) this.features.get()).addAll(insertedFeatures); // append features
    withInserted(insertedFeatures.stream().map(XyzFeature::getId).collect(toList())); // overwrite inserted
    return this;
  }

  public @NotNull XyzFeatureCollection withUpdatedFeatures(
      final @NotNull List<? extends @NotNull XyzFeature> updatedFeatures) {
    ((List<XyzFeature>) this.features.get()).addAll(updatedFeatures); // append features
    withUpdated(updatedFeatures.stream().map(XyzFeature::getId).toList()); // overwrite updated
    return this;
  }

  public @NotNull XyzFeatureCollection withDeletedFeatures(
      final @NotNull List<? extends @NotNull XyzFeature> deletedFeatures) {
    ((List<XyzFeature>) this.features.get()).addAll(deletedFeatures); // append features
    withDeleted(deletedFeatures.stream().map(XyzFeature::getId).toList()); // overwrite deleted
    return this;
  }

  public @Nullable List<XyzFeature> getViolations() {
    return violations;
  }

  public void setViolations(final @Nullable List<XyzFeature> violations) {
    this.violations = violations;
  }

  public @NotNull XyzFeatureCollection withViolations(final @Nullable List<XyzFeature> violations) {
    setViolations(violations);
    return this;
  }

  public static class ModificationFailure {

    private String id;
    private Long position;
    private String message;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @SuppressWarnings("unused")
    public ModificationFailure withId(String id) {
      setId(id);
      return this;
    }

    @SuppressWarnings("unused")
    public Long getPosition() {
      return position;
    }

    @SuppressWarnings("WeakerAccess")
    public void setPosition(Long position) {
      this.position = position;
    }

    @SuppressWarnings("unused")
    public ModificationFailure withPosition(Long position) {
      setPosition(position);
      return this;
    }

    @SuppressWarnings("unused")
    public String getMessage() {
      return message;
    }

    @SuppressWarnings("WeakerAccess")
    public void setMessage(String message) {
      this.message = message;
    }

    @SuppressWarnings("unused")
    public ModificationFailure withMessage(String message) {
      setMessage(message);
      return this;
    }
  }
}
