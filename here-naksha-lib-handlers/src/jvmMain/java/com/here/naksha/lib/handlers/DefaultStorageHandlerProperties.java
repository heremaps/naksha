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

import com.here.naksha.lib.core.CollectionRef;
import com.here.naksha.lib.core.ValueOrErr;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import naksha.base.NakshaError;
import naksha.base.PlatformType;
import naksha.model.Naksha;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

/**
 * Default variant of EventHandler properties supported by Naksha - default storage handler
 */
@AvailableSince(NakshaVersion.v2_0_7)
public class DefaultStorageHandlerProperties extends NakshaProperties {

  public static final PlatformType<DefaultStorageHandlerProperties> TYPE = forClass(DefaultStorageHandlerProperties.class);
  private static final Boolean DEFAULT_AUTO_CREATE_COLLECTION = true;
  private static final Boolean DEFAULT_AUTO_DELETE_COLLECTION = true;

  @Deprecated
  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String STORAGE_ID = "storageId";

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String COLLECTION = "collection";

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String AUTO_CREATE_COLLECTION = "autoCreateCollection";

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String AUTO_DELETE_COLLECTION = "autoDeleteCollection";

  /**
   * Read the collection reference defaults. To be used when the {@link SpaceProperties} do not explicitly define storage-id, map-id, and/or collection-id.
   *
   * <p>This method applies a downward compatibility hack, so that <code>v2</code> configurations will still work, next to the new configurations.
   * @since 3.0
   */
  // TODO: alweber: Review with Jakub
  public @NotNull ValueOrErr<CollectionRef> getCollectionRef() {
    @Nullable CollectionRef colRef = getAs(COLLECTION, CollectionRef.TYPE);
    if (colRef == null) {
      // If a URN was provided as `collection`, support it.
      final var string = getAs(COLLECTION, String_TYPE);
      if (string != null) {
        colRef = CollectionRef.fromString(string);
      }
    }
    // If the new collection syntax is not used, fallback to v2 downward compatibility.
    if (colRef == null) {
      // In v2 we expect that there is a dedicated `storageId` property at the handler, and a schema at the storage config.
      final var oldStorageId = getAs(STORAGE_ID, String_TYPE);
      if (oldStorageId != null) {
        colRef = new CollectionRef();
        if (colRef.getStorageId() == null) {
          colRef.setStorageId(oldStorageId);
        }
        // If that is the case, the default map is configured within the storage in a `schema` property.
        final var storage = Naksha.getStorageById(oldStorageId);
        if (storage == null) {
          // Fallback to old default schema.
          colRef.setMapId("unimap");
        } else {
          final var storageConfig = storage.getConfig();
          final var props = storageConfig.getProperties();
          final var schema = props.getAs("schema", String_TYPE);
          if (schema != null) {
            colRef.setMapId(schema);
          } else {
            // Fallback to old default schema.
            colRef.setMapId("unimap");
          }
        }
      }
    }
    if (colRef == null) {
      return new ValueOrErr<>(NakshaError.ILLEGAL_STATE, "");
    }
    return new ValueOrErr<>(colRef);
  }

  /**
   * Set the collection reference defaults.
   * @param ref the reference to set.
   * @since 3.0
   * @see CollectionRef
   */
  // TODO: alweber: Review with Jakub
  public void setCollectionRef(final @Nullable CollectionRef ref) {
    set(COLLECTION, ref);
  }

  /**
   * Indicates whether collection should be created automatically (happens on first collection's usage). By default: 'true'
   */
  public @NotNull Boolean getAutoCreateCollection() {
    return getOrSet(AUTO_CREATE_COLLECTION, DEFAULT_AUTO_CREATE_COLLECTION);
  }

  public void setAutoCreateCollection(@Nullable Boolean autoCreateCollection) {
    set(AUTO_CREATE_COLLECTION, autoCreateCollection);
  }

  /**
   * Indicates whether collection should be deleted automatically (happens when handler is deleted). By default: 'true'
   */
  public @NotNull Boolean getAutoDeleteCollection() {
    return getOrSet(AUTO_DELETE_COLLECTION, DEFAULT_AUTO_DELETE_COLLECTION);
  }

  public void setAutoDeleteCollection(@Nullable Boolean autoDeleteCollection) {
    set(AUTO_DELETE_COLLECTION, autoDeleteCollection);
  }
}
