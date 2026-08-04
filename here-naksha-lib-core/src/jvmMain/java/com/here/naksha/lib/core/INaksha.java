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
import naksha.base.NakshaException;
import naksha.model.IStorage;
import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaDatabase;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;

import static com.here.naksha.lib.core.HubInternalIdentifiers.*;

/**
 * The Naksha host interface. When an application bootstraps, it creates a Naksha host implementation and exposes it to the Naksha API. The
 * reference implementation is based upon the PostgresQL database, but alternative implementations are possible, for example the Naksha
 * extension library will fake a Naksha-Hub.
 */
@SuppressWarnings("unused")
public interface INaksha {

  /**
   * The `id` of the admin-catalog.
   * @deprecated Please replace with {@link #getAdminCatalog()}.
   */
  @Deprecated
  @NotNull String getAdminMapId();

  /**
   * Returns the admin database.
   * @return the admin database.
   * @throws NakshaException if not yet initialized.
   */
  @NotNull NakshaDatabase getAdminDatabase();

  /**
   * Returns the admin catalog.
   * @return the admin catalog.
   * @throws NakshaException if not yet initialized.
   */
  @NotNull NakshaCatalog getAdminCatalog();

  /**
   * Returns on the pre-defined admin-collections, see {@link HubInternalIdentifiers}.
   * @param collectionId the `id` of the collection to return.
   * @return the collection.
   * @throws NakshaException if not yet initialized or no such collection exists.
   */
  @NotNull
  NakshaCollection getAdminCollection(@NotNull String collectionId);

  /**
   * Returns the {@link HubInternalIdentifiers#SPACES spaces} collection descriptor.
   * <p><b>The returned object must not be modified!</b></p>
   * @return the collection descriptor.
   */
  default @NotNull NakshaCollection spacesCollection() { return getAdminCollection(SPACES); }
  /**
   * Returns the {@link HubInternalIdentifiers#CONFIGS configs} collection descriptor.
   * <p><b>The returned object must not be modified!</b></p>
   * @return the collection descriptor.
   */
  default @NotNull NakshaCollection configsCollection() { return getAdminCollection(CONFIGS); }
  /**
   * Returns the {@link HubInternalIdentifiers#SUBSCRIPTIONS subscriptions} collection descriptor.
   * <p><b>The returned object must not be modified!</b></p>
   * @return the collection descriptor.
   */
  default @NotNull NakshaCollection subscriptionsCollection() { return getAdminCollection(SUBSCRIPTIONS); }
  /**
   * Returns the {@link HubInternalIdentifiers#EVENT_HANDLERS event-handlers} collection descriptor.
   * <p><b>The returned object must not be modified!</b></p>
   * @return the collection descriptor.
   */
  default @NotNull NakshaCollection eventHandlersCollection() { return getAdminCollection(EVENT_HANDLERS); }
  /**
   * Returns the {@link HubInternalIdentifiers#STORAGES storages} collection descriptor.
   * <p><b>The returned object must not be modified!</b></p>
   * @return the collection descriptor.
   */
  default @NotNull NakshaCollection storagesCollection() { return getAdminCollection(STORAGES); }
  /**
   * Returns the {@link HubInternalIdentifiers#EXTENSIONS extensions} collection descriptor.
   * <p><b>The returned object must not be modified!</b></p>
   * @return the collection descriptor.
   */
  default @NotNull NakshaCollection extensionsCollection() { return getAdminCollection(EXTENSIONS); }

  /**
   * Returns a thin wrapper above the admin-database that adds authorization and internal event handling. Basically, this allows access to the admin collections.
   * @return the admin-storage.
   */
  @NotNull
  IStorage getAdminStorage();

  /**
   * Returns a virtual storage that maps spaces to collections and allows to execute requests in spaces.
   * @return the virtual space-storage.
   */
  @NotNull
  IStorage getSpaceStorage();

  /**
   * Returns the user defined space storage instance based on storageId as per space collection defined in Naksha admin storage.
   * @param storageId Id of the space storage
   * @return the space-storage
   */
  @NotNull
  IStorage getStorageById(final @NotNull String storageId);

  /**
   * Returns the configuration in use by NakshaHub
   * @return the config
   */
  @NotNull
  <T extends NakshaFeature> T getConfig();

  @NotNull
  ExtensionConfig getExtensionConfig();

  @NotNull
  ClassLoader getClassLoader(@NotNull String extensionId);
}
