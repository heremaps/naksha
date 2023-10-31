/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.lib.hub2;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import com.here.naksha.lib.core.IEventHandler;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.handlers.AuthorizationEventHandler;
import com.here.naksha.lib.handlers.IntHandlerForConfigs;
import com.here.naksha.lib.handlers.IntHandlerForEventHandlers;
import com.here.naksha.lib.handlers.IntHandlerForExtensions;
import com.here.naksha.lib.handlers.IntHandlerForSpaces;
import com.here.naksha.lib.handlers.IntHandlerForStorages;
import com.here.naksha.lib.handlers.IntHandlerForSubscriptions;
import com.here.naksha.lib.hub2.admin.AdminStorage;
import com.here.naksha.lib.hub2.space.SpaceStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class NakshaHub2 implements INaksha {

  private final @NotNull AdminStorage adminStorage;

  private final @NotNull SpaceStorage spaceStorage;

  NakshaHub2(@NotNull AdminStorage adminStorage, @NotNull SpaceStorage spaceStorage) {
    this.adminStorage = adminStorage;
    this.spaceStorage = spaceStorage;
    spaceStorage.setVirtualSpaces(configureVirtualSpaces(this));
    spaceStorage.setEventPipelineFactory(new NakshaEventPipelineFactory(this));
  }

  /**
   * Returns a thin wrapper above the admin-database that adds authorization and internal event handling. Basically, this allows access to
   * the admin collections.
   *
   * @return the admin-storage.
   */
  @Override
  public @NotNull IStorage getAdminStorage() {
    return adminStorage;
  }

  /**
   * Returns a virtual storage that maps spaces to collections and allows to execute requests in spaces.
   *
   * @return the virtual space-storage.
   */
  @Override
  public @NotNull IStorage getSpaceStorage() {
    return spaceStorage;
  }

  /**
   * Returns the user defined space storage instance based on storageId as per space collection defined in Naksha admin storage.
   *
   * @param storageId Id of the space storage
   * @return the space-storage
   */
  @Override
  public @NotNull IStorage getStorageById(@NotNull String storageId) {
    // TODO : Add logic to retrieve Storage from Admin DB and then instantiate respective IStorage implementation
    return null;
  }

  private static @NotNull Map<String, List<IEventHandler>> configureVirtualSpaces(final @NotNull INaksha hub) {
    final Map<String, List<IEventHandler>> adminSpaces = new HashMap<>();
    // common auth handler
    final IEventHandler authHandler = new AuthorizationEventHandler(hub);
    // add event handlers for each admin space
    for (final String spaceId : NakshaAdminCollection.ALL) {
      adminSpaces.put(
          spaceId,
          switch (spaceId) {
            case NakshaAdminCollection.CONFIGS -> List.of(authHandler, new IntHandlerForConfigs(hub));
            case NakshaAdminCollection.SPACES -> List.of(authHandler, new IntHandlerForSpaces(hub));
            case NakshaAdminCollection.SUBSCRIPTIONS -> List.of(
                authHandler, new IntHandlerForSubscriptions(hub));
            case NakshaAdminCollection.EVENT_HANDLERS -> List.of(
                authHandler, new IntHandlerForEventHandlers(hub));
            case NakshaAdminCollection.STORAGES -> List.of(authHandler, new IntHandlerForStorages(hub));
            case NakshaAdminCollection.EXTENSIONS -> List.of(authHandler, new IntHandlerForExtensions(hub));
            default -> throw unchecked(new Exception("Unsupported virtual space " + spaceId));
          });
    }
    return adminSpaces;
  }
}
