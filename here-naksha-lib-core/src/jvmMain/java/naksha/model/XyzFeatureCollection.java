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

import static naksha.base.NakshaBaseKt.*;
import static naksha.base.Platform.forClass;

import com.here.naksha.lib.core.models.payload.XyzResponse;

import java.util.List;

import naksha.base.*;
import naksha.geo.BBox;
import naksha.geo.GeoFeature;
import naksha.geo.GeoFeatureList;
import naksha.model.objects.NakshaFeatureList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XyzFeatureCollection extends XyzResponse {

  public static final PlatformType<XyzFeatureCollection> TYPE = forClass(XyzFeatureCollection.class);

  @Override
  public void onCreation() {
    super.onCreation();
    setFeatures(new NakshaFeatureList());
  }

  public static final String INSERTED = "inserted";
  public static final String UPDATED = "inserted";
  public static final String DELETED = "deleted";
  private static final NotNullProperty<XyzFeatureCollection, StringList> STRING_LIST$
      = new NotNullProperty<>(StringList.TYPE, null, (self, name) -> new StringList() );

  public static final String FAILED = "failed";
  private static final NullableProperty<XyzFeatureCollection, ModificationFailureList> FAILED$
      = new NullableProperty<>(ModificationFailureList.TYPE, FAILED, false, true);

  private static final NullableProperty<XyzFeatureCollection, Integer> VERSION$
      = new NullableProperty<>(Int_TYPE, "version", false, true);

  public static final String OLD_FEATURES = "oldFeatures";
  public static final String VIOLATIONS = "violations";

  private static final NullableProperty<XyzFeatureCollection, BBox> BBOX$
      = new NullableProperty<>(BBox.TYPE, "bbox", false, true);

  public @Nullable BBox getBbox() {
    return BBOX$.getValue(this);
  }
  public void setBbox(@Nullable BBox bbox) {
    BBOX$.setValue(this, bbox);
  }

  private static final NullableProperty<XyzFeatureCollection, String> HANDLE$
      = new NullableProperty<>(String_TYPE, "handle", false, true);

  /**
   * Returns the Space handle which is used to iterate above data.
   *
   * @return the handle.
   * @deprecated use {@link #getNextPageToken()} instead.
   */
  public @Nullable String getHandle() {
    return HANDLE$.getValue(this);
  }

  /**
   * Sets the Space handle that can be used to continue an iterate.
   *
   * @param handle the handle, if null the handle property is removed.
   * @deprecated use {@link #setNextPageToken(String)} instead.
   */
  @SuppressWarnings("WeakerAccess")
  public void setHandle(@Nullable String handle) {
    HANDLE$.setValue(this, handle);
  }

  private static final NullableProperty<XyzFeatureCollection, String> NEXT_PAGE_TOKEN$
      = new NullableProperty<>(String_TYPE, "nextPageToken", false, true);

  /**
   * Returns the Space nextPageToken which is used to iterate above data.
   *
   * @return the nextPageToken.
   */
  public @Nullable String getNextPageToken() {
    return NEXT_PAGE_TOKEN$.getValue(this);
  }

  /**
   * Sets the Space nextPageToken that can be used to continue an iterate.
   *
   * @param nextPageToken the nextPageToken, if null the nextPageToken property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setNextPageToken(@Nullable String nextPageToken) {
    NEXT_PAGE_TOKEN$.setValue(this, nextPageToken);
  }

  private static final NotNullBoolProperty<XyzFeatureCollection> PARTIAL$
      = new NotNullBoolProperty<>("partial", false, true);

  /**
   * Returns true if FeatureCollection does not contain all results. Is used for tweaks.
   *
   * @return the handle.
   */
  public @NotNull Boolean isPartial() {
    return PARTIAL$.getValue(this);
  }

  /**
   * Set indication if FeatureCollection has all expected results or not.
   *
   * @param partial is true if FeatureCollection does not contain all data.
   */
  @SuppressWarnings("WeakerAccess")
  public void setPartial(@NotNull Boolean partial) {
    PARTIAL$.setValue(this, partial);
  }

  public static final String COUNT = "count";
  private static final NotNullProperty<XyzFeatureCollection, Long> COUNT$
      = new NotNullProperty<>(Long_TYPE, COUNT, (self, name) -> 0L );

  /**
   * Returns the proprietary count property that is used by Space count requests to return the number of features found.
   *
   * @return the amount of features that are matching the query.
   */
  public @Nullable Long getCount() {
    return COUNT$.getValue(this);
  }

  /**
   * Sets the amount of features that where matching a query, without returning the features (so features will be null or an empty array).
   *
   * @param count the amount of features that where matching a query, if null, then the property is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setCount(@Nullable Long count) {
    if (count == null) {
      delete(COUNT);
    } else {
      COUNT$.setValue(this, count);
    }
  }

  private <F extends GeoFeature, LIST extends List<F>> void addFeaturesAndIds(@NotNull StringList ids, @Nullable LIST featuresToAdd) {
    if (featuresToAdd == null) return;
    final var features = getFeatures(GeoFeatureList.TYPE);
    for (final F feature : featuresToAdd) {
      final var id = feature.getId();
      if (!ids.contains(id)) {
        ids.add(id);
        features.add(feature);
      }
    }
  }

  /**
   * @return list of features IDs of those features that where successfully inserted.
   */
  public @NotNull StringList getInserted() {
    return STRING_LIST$.getValue(this, INSERTED);
  }

  /**
   * Sets the list of successfully inserted feature IDs.
   *
   * @param inserted the IDs of the features that where inserted.
   */
  @SuppressWarnings("WeakerAccess")
  public void setInserted(@Nullable List<String> inserted) {
    STRING_LIST$.setValue(this, INSERTED, ListProxy.to(StringList.TYPE, inserted));
  }

  public <F extends GeoFeature, LIST extends List<F>> void addInsertedFeatures(@Nullable LIST inserted) {
    addFeaturesAndIds(getInserted(), inserted);
  }

  /**
   * @return list of features IDs of those features that where successfully updated.
   */
  public @NotNull StringList getUpdated() {
    return STRING_LIST$.getValue(this, "updated");
  }

  /**
   * Sets the list of successfully updated feature IDs.
   *
   * @param updated the IDs of the features that where updated.
   */
  public void setUpdated(@Nullable List<String> updated) {
    STRING_LIST$.setValue(this, UPDATED, ListProxy.to(StringList.TYPE, updated));
  }

  public <F extends GeoFeature, LIST extends List<F>> void addUpdatedFeatures(@Nullable LIST updated) {
    addFeaturesAndIds(getUpdated(), updated);
  }

  /**
   * Appends the given feature ID into the list of updated
   *
   * @param updateId the ID to be inserted into the list
   */
  public void appendUpdateId(@NotNull String updateId) {
    STRING_LIST$.getValue(this, UPDATED).append(updateId);
  }

  /**
   * @return list of features IDs of those features that where successfully deleted.
   */
  public @NotNull StringList getDeleted() {
    return STRING_LIST$.getValue(this, DELETED);
  }

  /**
   * Sets the list of successfully deleted feature IDs.
   *
   * @param deleted the IDs of the features that where deleted.
   */
  public void setDeleted(@Nullable List<String> deleted) {
    STRING_LIST$.setValue(this, DELETED, ListProxy.to(StringList.TYPE, deleted));
  }

  public <F extends GeoFeature, LIST extends List<F>> void addDeletedFeatures(@Nullable LIST deleted) {
    addFeaturesAndIds(getDeleted(), deleted);
  }

  /**
   * @return A list of modification failures
   */
  public @Nullable ModificationFailureList getFailed() {
    return FAILED$.getValue(this);
  }

  @SuppressWarnings("WeakerAccess")
  public void setFailed(@Nullable List<ModificationFailure> failed) {
    FAILED$.setValue(this, FAILED, ListProxy.toNullable(ModificationFailureList.TYPE, failed));
  }

  /**
   * For FeatureCollection write-responses: If the history of a space is activated and this FeatureCollection is a response to a
   * modification of the space - contains the (new) space-version which has just been written.
   *
   * @return The new space-version after some modification
   */
  public @Nullable Integer getVersion() {
    return VERSION$.getValue(this);
  }

  public void setVersion(@Nullable Integer version) {
    VERSION$.setValue(this, version);
  }

  public <F extends GeoFeature, LIST extends ListProxy<F>> @Nullable LIST getOldFeatures(PlatformType<LIST> type) {
    final var raw = getRaw(OLD_FEATURES);
    return raw instanceof PlatformList ? type.proxy((PlatformList) raw) : null;
  }

  public <F extends GeoFeature, LIST extends List<F>> void setOldFeatures(@Nullable LIST list) {
    if (list == null) {
      delete(OLD_FEATURES);
    } else {
      set(OLD_FEATURES, ListProxy.toNullable(GeoFeatureList.TYPE, list));
    }
  }

  public <F extends GeoFeature, LIST extends ListProxy<F>> @Nullable LIST getViolations(PlatformType<LIST> type) {
    final var raw = getRaw(VIOLATIONS);
    return raw instanceof PlatformList ? type.proxy((PlatformList) raw) : null;
  }

  public <F extends GeoFeature, LIST extends List<F>> void setViolations(@Nullable LIST list) {
    if (list == null) {
      delete(VIOLATIONS);
    } else {
      set(VIOLATIONS, ListProxy.toNullable(GeoFeatureList.TYPE, list));
    }
  }
}