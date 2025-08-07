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
package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import naksha.base.PlatformType;
import naksha.model.IStorage;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.Platform.forClass;

/**
 * Default variant of EventHandler properties supported by Naksha - default storage handler
 */
@AvailableSince(NakshaVersion.v2_0_7)
public class DefaultStorageHandlerProperties extends NakshaProperties {

  public static final PlatformType<DefaultStorageHandlerProperties> TYPE = forClass(DefaultStorageHandlerProperties.class);
  private static final Boolean DEFAULT_AUTO_CREATE_COLLECTION = true;
  private static final Boolean DEFAULT_AUTO_DELETE_COLLECTION = true;

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String STORAGE_ID = "storageId";

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String COLLECTION = "collection";

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String AUTO_CREATE_COLLECTION = "autoCreateCollection";

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String AUTO_DELETE_COLLECTION = "autoDeleteCollection";

  /**
   * To associate EventHandler with specific {@link IStorage} that it should operate against.
   */
  public @Nullable String getStorageId() {
    return (String) getRaw(STORAGE_ID);
  }

  public void setStorageId(final @Nullable String storageId) {
    setRaw(STORAGE_ID, storageId);
  }

  /**
   * Details of the backend xyz collection to use. If undefined, the collection defined at the {@link SpaceProperties} level will be used.
   */
  public @Nullable NakshaCollection getCollection() {
    return getAs(COLLECTION, NakshaCollection.TYPE);
  }

  public void setCollection(final @Nullable NakshaCollection collection) {
    setRaw(COLLECTION, collection);
  }

  /**
   * Indicates whether collection should be created automatically (happens on first collection's usage). By default: 'true'
   */
  public @NotNull Boolean getAutoCreateCollection() {
    return getOrSet(AUTO_CREATE_COLLECTION, DEFAULT_AUTO_CREATE_COLLECTION);
  }

  public void setAutoCreateCollection(Boolean autoCreateCollection) {
    setRaw(AUTO_CREATE_COLLECTION, autoCreateCollection);
  }

  /**
   * Indicates whether collection should be deleted automatically (happens when handler is deleted). By default: 'true'
   */
  public @NotNull Boolean getAutoDeleteCollection() {
    return getOrSet(AUTO_DELETE_COLLECTION, DEFAULT_AUTO_DELETE_COLLECTION);
  }

  public void setAutoDeleteCollection(Boolean autoDeleteCollection) {
    setRaw(AUTO_DELETE_COLLECTION, autoDeleteCollection);
  }
}
