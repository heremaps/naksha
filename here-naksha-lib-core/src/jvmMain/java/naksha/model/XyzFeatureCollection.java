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
package naksha.model;

import static java.util.stream.Collectors.toList;
import static naksha.base.NakshaBaseKt.*;
import static naksha.base.Platform.forClass;

import com.here.naksha.lib.core.models.payload.XyzResponse;
import java.util.List;

import naksha.base.*;
import naksha.geo.BBox;
import naksha.geo.GeoFeature;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XyzFeatureCollection extends XyzResponse {
  @Override
  public <F extends GeoFeature, LIST extends ListProxy<F>> @NotNull XyzFeatureCollection withFeatures(@NotNull LIST list) {
    super.withFeatures(list);
    return this;
  }


  public static final PlatformType<XyzFeatureCollection> TYPE = forClass(XyzFeatureCollection.class)
      .withJsonType("FeatureCollection");

  @Override
  public void onCreation() {
    super.onCreation();
    setFeatures(new NakshaFeatureList());
  }

  public @Nullable BBox getBbox() {
    return getAs("bbox", BBox.TYPE);
  }

  public void setBbox(@Nullable BBox bbox) {
    set("bbox", bbox);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withBbox(final BBox bbox) {
    setBbox(bbox);
    return this;
  }

  /**
   * Returns the Space handle which is used to iterate above data.
   *
   * @return the handle.
   * @deprecated use {@link #getNextPageToken()} instead.
   */
  public @Nullable String getHandle() {
    return getAs("handle", String_TYPE);
  }

  /**
   * Sets the Space handle that can be used to continue an iterate.
   *
   * @param handle the handle, if null the handle property is removed.
   * @deprecated use {@link #setNextPageToken(String)} instead.
   */
  @SuppressWarnings("WeakerAccess")
  public void setHandle(@Nullable String handle) {
    set("handle", handle);
  }

  /**
   * @deprecated use {@link #withNextPageToken(String)} instead.
   */
  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withHandle(final @Nullable String handle) {
    setHandle(handle);
    return this;
  }

  /**
   * Returns the Space nextPageToken which is used to iterate above data.
   *
   * @return the nextPageToken.
   */
  public @Nullable String getNextPageToken() {
    return getAs("nextPageToken", String_TYPE);
  }

  /**
   * Sets the Space nextPageToken that can be used to continue an iterate.
   *
   * @param nextPageToken the nextPageToken, if null the nextPageToken property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setNextPageToken(@Nullable String nextPageToken) {
    set("nextPageToken", nextPageToken);
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withNextPageToken(final @Nullable String nextPageToken) {
    setNextPageToken(nextPageToken);
    return this;
  }

  /**
   * Returns true if FeatureCollection does not contain all results. Is used for tweaks.
   *
   * @return the handle.
   */
  public @NotNull Boolean isPartial() {
    return getOr("partial", Boolean.FALSE);
  }

  /**
   * Set indication if FeatureCollection has all expected results or not.
   *
   * @param partial is true if FeatureCollection does not contains all data.
   */
  @SuppressWarnings("WeakerAccess")
  public void setPartial(@NotNull Boolean partial) {
    set("partial", partial);
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withPartial(final @NotNull Boolean partial) {
    setPartial(partial);
    return this;
  }

  /**
   * Returns the proprietary count property that is used by Space count requests to return the number of features found.
   *
   * @return the amount of features that are matching the query.
   */
  public @Nullable Long getCount() {
    return getAs("count", Long_TYPE);
  }

  /**
   * Sets the amount of features that where matching a query, without returning the features (so features will be null or an empty array).
   *
   * @param count the amount of features that where matching a query, if null, then the property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setCount(@Nullable Long count) {
    if (count == null) {
      delete("count");
    } else {
      set("count", count);
    }
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withCount(final @Nullable Long count) {
    setCount(count);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully inserted.
   */
  public @Nullable StringList getInserted() {
    return getAs("inserted", StringList.TYPE);
  }

  /**
   * Sets the list of successfully inserted feature IDs.
   *
   * @param inserted the IDs of the features that where inserted.
   */
  @SuppressWarnings("WeakerAccess")
  public void setInserted(@Nullable List<String> inserted) {
    setNullableList("inserted", inserted, StringList.TYPE);
  }

  /**
   * Appends the given feature ID into the list of inserted
   *
   * @param insertId the ID to be inserted into the list
   */
  public void appendInsertId(@NotNull String insertId) {
    appendNullableStringList("inserted", insertId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withInserted(List<String> inserted) {
    setInserted(inserted);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully updated.
   */
  public @Nullable StringList getUpdated() {
    return getAs("updated", StringList.TYPE);
  }

  /**
   * Sets the list of successfully updated feature IDs.
   *
   * @param updated the IDs of the features that where updated.
   */
  public void setUpdated(@Nullable List<String> updated) {
    setNullableList("updated", updated, StringList.TYPE);
  }

  /**
   * Appends the given feature ID into the list of updated
   *
   * @param updateId the ID to be inserted into the list
   */
  public void appendUpdateId(@NotNull String updateId) {
    appendNullableStringList("updated", updateId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withUpdated(@Nullable List<String> updated) {
    setUpdated(updated);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully deleted.
   */
  public @Nullable StringList getDeleted() {
    return getAs("deleted", StringList.TYPE);
  }

  /**
   * Sets the list of successfully deleted feature IDs.
   *
   * @param deleted the IDs of the features that where deleted.
   */
  public void setDeleted(@Nullable List<String> deleted) {
    setNullableList("deleted", deleted);
  }

  /**
   * Appends the given feature ID into the list of deleted
   *
   * @param deleteId the ID to be inserted into the list
   */
  public void appendDeleteId(@NotNull String deleteId) {
    appendNullableStringList("deleted", deleteId);
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withDeleted(@Nullable List<String> deleted) {
    setDeleted(deleted);
    return this;
  }

  /**
   * @return A list of modification failures
   */
  public ModificationFailureList getFailed() {
    return getAs("failed", ModificationFailureList.TYPE);
  }

  @SuppressWarnings("WeakerAccess")
  public void setFailed(@NotNull List<ModificationFailure> failed) {
    setNullableStringList("failed", failed);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withFailed(List<ModificationFailure> failed) {
    setFailed(failed);
    return this;
  }

  /**
   * For FeatureCollection write-responses: If the history of a space is activated and this FeatureCollection is a response to a
   * modification of the space - contains the (new) space-version which has just been written.
   *
   * @return The new space-version after some modification
   */
  public Integer getVersion() {
    return VERSION.getValue(this);
  }

  public void setVersion(int version) {
    VERSION.setValue(this, version);
  }

  public XyzFeatureCollection withVersion(int version) {
    setVersion(version);
    return this;
  }

  @SuppressWarnings("unused")
  public List<NakshaFeature> getOldFeatures() {
    return OLD_FEATURES.getValue(this);
  }

  @SuppressWarnings("WeakerAccess")
  public void setOldFeatures(List<NakshaFeature> oldFeatures) {
    setOldFeatures(NakshaFeatureList.fromList(oldFeatures));
  }

  public void setOldFeatures(NakshaFeatureList oldFeatures) {
    OLD_FEATURES.setValue(this, oldFeatures);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withOldFeatures(List<NakshaFeature> oldFeatures) {
    setOldFeatures(oldFeatures);
    return this;
  }

  @SuppressWarnings("unused")
  public @NotNull XyzFeatureCollection withInsertedFeatures(
      final @NotNull List<? extends @NotNull NakshaFeature> insertedFeatures) {
    getFeatures().addAll(insertedFeatures); // append features
    setInserted(insertedFeatures.stream().map(NakshaFeature::getId).collect(toList())); // overwrite inserted
    return this;
  }

  public @NotNull XyzFeatureCollection withUpdatedFeatures(
      final @NotNull List<? extends @NotNull NakshaFeature> updatedFeatures) {
    getFeatures().addAll(updatedFeatures); // append features
    setUpdated(updatedFeatures.stream().map(NakshaFeature::getId).collect(toList())); // overwrite updated
    return this;
  }

  public @NotNull XyzFeatureCollection withDeletedFeatures(
      final @NotNull List<? extends @NotNull NakshaFeature> deletedFeatures) {
    getFeatures().addAll(deletedFeatures); // append features
    setDeleted(deletedFeatures.stream().map(NakshaFeature::getId).collect(toList())); // overwrite deleted
    return this;
  }

  public @Nullable List<NakshaFeature> getViolations() {
    return VIOLATIONS.getValue(this);
  }

  public void setViolations(final @Nullable List<NakshaFeature> violations) {
    if(violations == null){
      setViolations(null);
    } else {
      setViolations(NakshaFeatureList.fromList(violations));
    }
  }

  public void setViolations(final @Nullable NakshaFeatureList violations) {
    VIOLATIONS.setValue(this, violations);
  }

  public @NotNull XyzFeatureCollection withViolations(final @Nullable List<NakshaFeature> violations) {
    setViolations(violations);
    return this;
  }

  private <E, LIST extends ListProxy<E>> void setNullableList(
      final @NotNull String property,
      final @Nullable List<E> elements,
      final @NotNull PlatformType<LIST> listType
  ) {
    if (elements == null) {
      delete(property);
    } else {
      set(property, ListProxy.to(elements, listType));
    }
  }

  private void appendNullableStringList(@NotNull String property, @NotNull String element) {
    var list = getAs(property, StringList.TYPE);
    if (list == null) {
      list = new StringList();
      set(property, list);
    }
    list.add(element);
  }

  public static class ModificationFailureList extends ListProxy<ModificationFailure> {
    public static final PlatformType<ModificationFailureList> TYPE = forClass(ModificationFailureList.class);

    public ModificationFailureList() {
      super(ModificationFailure.TYPE);
    }

    public ModificationFailureList(List<ModificationFailure> failures) {
      this();
      addAll(failures);
    }
  }

  public static class ModificationFailure extends AnyObject {
    public static final PlatformType<ModificationFailure> TYPE = forClass(ModificationFailure.class);
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
    public @NotNull ModificationFailure withId(String id) {
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
