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
package com.here.naksha.lib.core;

import com.here.naksha.lib.core.models.ExtensionConfig;
import naksha.model.IStorage;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;

/**
 * The Naksha host interface. When an application bootstraps, it creates a Naksha host implementation and exposes it to the Naksha API.
 * @since 2.0
 */
@SuppressWarnings("unused")
public interface INaksha {
  /**
   * Returns the <code>id</code> of the admin-map. The admin-map is the map in which Naksha-Hub does store administrative data, like spaces, registered custom storages, handlers, ...
   * @return the <code>id</code> of the admin-map.
   * @since 3.0
   */
  @NotNull
  String getAdminMapId();

  /**
   * Returns a thin wrapper above the admin-database that adds authorization and internal event handling. Basically, this allows access to the admin collections.
   * @return the admin-storage.
   * @since 2.0
   */
  @NotNull
  IStorage getAdminStorage();

  // TODO: alweber: Improve the documentation, it is not really clear what this means?
  //       Specifically: Assume I want to send a ReadFeatures request to the virtual space storage,
  //       the collection-id should be matching the space-id, but what map-id do I use?
  //       So, I assume, the request I send here, ends up in the event pipeline of the space with
  //       given collection-id (so collection-id == space-id), but what is the map-id?
  //       If I should guess, I would assume to use what `getAdminMapId` returns?
  /**
   * Returns a virtual storage that maps spaces to collections and allows to execute requests in spaces.
   * @return the virtual space-storage.
   * @since 2.0
   */
  @NotNull
  IStorage getSpaceStorage();

  // TODO: alweber: We should improve the documentation. I assume, this simply returns the singleton for a specific NakshaStorage.
  //       When this is true, the documentation should be simplified. Apart, @NotNull means, it throws some exception when no such
  //       storage exists? Should at least be documented!
  /**
   * Returns the user defined space storage instance based on storageId as per space collection defined in Naksha admin storage.
   * @param storageId Id of the space storage
   * @return the space-storage
   * @since 2.0
   */
  @NotNull
  IStorage getStorageById(final @NotNull String storageId);

  /**
   * Returns the configuration in use by Naksha-Hub.
   * @return the config
   * @since 2.0
   */
  @NotNull
  <T extends NakshaFeature> T getConfig();

  // TODO: Add documentation, what is this about?
  @NotNull
  ExtensionConfig getExtensionConfig();

  // TODO: Add documentation, what is this?
  @NotNull
  ClassLoader getClassLoader(@NotNull String extensionId);

  /**
   * Returns a helper that simplifies certain often performed operations. There is a default implementation ({@link NakshaQuickAccess}), but the implementation is allowed to create their own special implementation, optimized internally, which is why the abstraction of an interface and implementation. Additionally, this can simplify testing, because it is easier to mock these methods, than to implement a full storage.
   *
   * <p>The default implementation does look like:
   * <pre>{@code
   * private final NakshaQuickAccess nakshaQuickAccess =
   *     new NakshaQuickAccess(this);
   *
   * @Override
   * public @NotNull INakshaQuickAccess quickAccess() {
   *   return nakshaQuickAccess;
   * }
   * }</pre>
   * @return a helper that simplifies certain often performed operations.
   * @since 3.0
   */
  @NotNull INakshaQuickAccess quickAccess();
}