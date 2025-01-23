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

import com.here.naksha.lib.core.models.payload.XyzResponse;
import java.util.List;
import naksha.base.AnyObject;
import naksha.base.JvmListProxy;
import naksha.base.JvmPropertyUtil;
import naksha.base.NotNullProperty;
import naksha.base.NullableProperty;
import naksha.base.StringList;
import naksha.geo.SpBoundingBox;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XyzFeatureCollection extends XyzResponse {

  private static final String FEATURE_COLLECTION_TYPE = "FeatureCollection";

  private static final NotNullProperty<XyzFeatureCollection, String> TYPE =
      JvmPropertyUtil.notNullProperty(String.class, "type", (xfc, name) -> FEATURE_COLLECTION_TYPE);

  private static final NotNullProperty<XyzFeatureCollection, NakshaFeatureList> FEATURES =
      JvmPropertyUtil.notNullProperty(
          NakshaFeatureList.class, "features", (xfc, name) -> new NakshaFeatureList());

  private static final NullableProperty<XyzFeatureCollection, SpBoundingBox> BBOX =
      JvmPropertyUtil.nullableProperty(SpBoundingBox.class, "bbox");

  private static final NullableProperty<XyzFeatureCollection, Boolean> PARTIAL =
      JvmPropertyUtil.nullableProperty(Boolean.class, "partial");

  private static final NullableProperty<XyzFeatureCollection, String> HANDLE =
      JvmPropertyUtil.nullableProperty(String.class, "handle");

  private static final NullableProperty<XyzFeatureCollection, String> NEXT_PAGE_TOKEN =
      JvmPropertyUtil.nullableProperty(String.class, "nextPageToken");

  private static final NullableProperty<XyzFeatureCollection, Long> COUNT =
      JvmPropertyUtil.nullableProperty(Long.class, "count");

  private static final NullableProperty<XyzFeatureCollection, StringList> INSERTED =
      JvmPropertyUtil.nullableProperty(StringList.class, "inserted");

  private static final NullableProperty<XyzFeatureCollection, StringList> UPDATED =
      JvmPropertyUtil.nullableProperty(StringList.class, "updated");

  private static final NullableProperty<XyzFeatureCollection, StringList> DELETED =
      JvmPropertyUtil.nullableProperty(StringList.class, "deleted");

  private static final NullableProperty<XyzFeatureCollection, NakshaFeatureList> OLD_FEATURES =
      JvmPropertyUtil.nullableProperty(NakshaFeatureList.class, "oldFeatures");

  private static final NullableProperty<XyzFeatureCollection, NakshaFeatureList> VIOLATIONS =
      JvmPropertyUtil.nullableProperty(NakshaFeatureList.class, "violations");

  private static final NullableProperty<XyzFeatureCollection, ModificationFailureList> FAILED =
      JvmPropertyUtil.nullableProperty(ModificationFailureList.class, "failed");

  private static final NullableProperty<XyzFeatureCollection, Integer> VERSION =
      JvmPropertyUtil.nullableProperty(Integer.class, "version");

  @Override
  public void onCreation() {
    setFeatures(new NakshaFeatureList());
    TYPE.setValue(this, FEATURE_COLLECTION_TYPE);
  }

  public SpBoundingBox getBbox() {
    return BBOX.getValue(this);
  }

  public void setBbox(SpBoundingBox bbox) {
    BBOX.setValue(this, bbox);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withBbox(final SpBoundingBox bbox) {
    setBbox(bbox);
    return this;
  }

  public @NotNull List<NakshaFeature> getFeatures() {
    return FEATURES.getValue(this);
  }

  public void setFeatures(@NotNull List<? extends NakshaFeature> features) {
    NakshaFeatureList nakshaFeatureList = new NakshaFeatureList();
    nakshaFeatureList.addAll(features);
    FEATURES.setValue(this, nakshaFeatureList);
  }

  public void setFeatures(@NotNull NakshaFeatureList features) {
    FEATURES.setValue(this, features);
  }

  public @NotNull XyzFeatureCollection withFeatures(NakshaFeatureList features) {
    setFeatures(features);
    return this;
  }

  public @NotNull XyzFeatureCollection withFeatures(final @NotNull List<? extends @NotNull NakshaFeature> features) {
    setFeatures(features);
    return this;
  }

  /**
   * Returns the Space handle which is used to iterate above data.
   *
   * @return the handle.
   * @deprecated use {@link #getNextPageToken()} instead.
   */
  public @Nullable String getHandle() {
    return HANDLE.getValue(this);
  }

  /**
   * Sets the Space handle that can be used to continue an iterate.
   *
   * @param handle the handle, if null the handle property is removed.
   * @deprecated use {@link #setNextPageToken(String)} instead.
   */
  @SuppressWarnings("WeakerAccess")
  public void setHandle(String handle) {
    HANDLE.setValue(this, handle);
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
    return NEXT_PAGE_TOKEN.getValue(this);
  }

  /**
   * Sets the Space nextPageToken that can be used to continue an iterate.
   *
   * @param nextPageToken the nextPageToken, if null the nextPageToken property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setNextPageToken(String nextPageToken) {
    NEXT_PAGE_TOKEN.setValue(this, nextPageToken);
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
    return PARTIAL.getValue(this);
  }

  /**
   * Set indication if FeatureCollection has all expected results or not.
   *
   * @param partial is true if FeatureCollection does not contains all data.
   */
  @SuppressWarnings("WeakerAccess")
  public void setPartial(Boolean partial) {
    PARTIAL.setValue(this, partial);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withPartial(final Boolean partial) {
    setPartial(partial);
    return this;
  }

  /**
   * Returns the proprietary count property that is used by Space count requests to return the number of features found.
   *
   * @return the amount of features that are matching the query.
   */
  public Long getCount() {
    return COUNT.getValue(this);
  }

  /**
   * Sets the amount of features that where matching a query, without returning the features (so features will be null or an empty array).
   *
   * @param count the amount of features that where matching a query, if null, then the property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setCount(Long count) {
    put("count", count);
    COUNT.setValue(this, count);
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
    return INSERTED.getValue(this);
  }

  /**
   * Sets the list of successfully inserted feature IDs.
   *
   * @param inserted the IDs of the features that where inserted.
   */
  @SuppressWarnings("WeakerAccess")
  public void setInserted(List<String> inserted) {
    setInserted(StringList.fromList(inserted));
  }

  public void setInserted(StringList inserted) {
    INSERTED.setValue(this, inserted);
  }

  /**
   * Appends the given feature ID into the list of inserted
   *
   * @param insertId the ID to be inserted into the list
   */
  public void appendInsertId(@NotNull String insertId) {
    appendToNullableStringList(INSERTED, insertId);
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
    return UPDATED.getValue(this);
  }

  public void setUpdated(List<String> updated) {
    UPDATED.setValue(this, StringList.fromList(updated));
  }

  /**
   * Sets the list of successfully updated feature IDs.
   *
   * @param updated the IDs of the features that where updated.
   */
  public void setUpdated(StringList updated) {
    UPDATED.setValue(this, updated);
  }

  /**
   * Appends the given feature ID into the list of updated
   *
   * @param updateId the ID to be inserted into the list
   */
  public void appendUpdateId(@NotNull String updateId) {
    appendToNullableStringList(UPDATED, updateId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withUpdated(List<String> updated) {
    setUpdated(updated);
    return this;
  }

  /**
   * @return list of features IDs of those features that where successfully deleted.
   */
  public StringList getDeleted() {
    return DELETED.getValue(this);
  }

  public void setDeleted(List<String> deleted) {
    setDeleted(StringList.fromList(deleted));
  }

  /**
   * Sets the list of successfully deleted feature IDs.
   *
   * @param deleted the IDs of the features that where deleted.
   */
  public void setDeleted(StringList deleted) {
    DELETED.setValue(this, deleted);
  }

  /**
   * Appends the given feature ID into the list of deleted
   *
   * @param deleteId the ID to be inserted into the list
   */
  public void appendDeleteId(@NotNull String deleteId) {
    appendToNullableStringList(DELETED, deleteId);
  }

  @SuppressWarnings("unused")
  public XyzFeatureCollection withDeleted(List<String> deleted) {
    setDeleted(deleted);
    return this;
  }

  /**
   * @return A list of modification failures
   */
  public ModificationFailureList getFailed() {
    return FAILED.getValue(this);
  }

  @SuppressWarnings("WeakerAccess")
  public void setFailed(List<ModificationFailure> failed) {
    FAILED.setValue(this, new ModificationFailureList(failed));
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
    setViolations(NakshaFeatureList.fromList(violations));
  }

  public void setViolations(final @Nullable NakshaFeatureList violations) {
    VIOLATIONS.setValue(this, violations);
  }

  public @NotNull XyzFeatureCollection withViolations(final @Nullable List<NakshaFeature> violations) {
    setViolations(violations);
    return this;
  }

  private void appendToNullableStringList(
      NullableProperty<XyzFeatureCollection, StringList> stringsProperty, String element) {
    StringList stringList = stringsProperty.getValue(this);
    if (stringList == null) {
      stringList = new StringList();
      stringsProperty.setValue(this, stringList);
    }
    stringList.add(element);
  }

  public static class ModificationFailureList extends JvmListProxy<ModificationFailure> {

    public ModificationFailureList() {
      super(ModificationFailure.class);
    }

    public ModificationFailureList(List<ModificationFailure> failures) {
      this();
      addAll(failures);
    }
  }

  public static class ModificationFailure extends AnyObject {

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
