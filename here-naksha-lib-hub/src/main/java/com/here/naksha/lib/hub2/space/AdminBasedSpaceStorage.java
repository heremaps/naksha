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
package com.here.naksha.lib.hub2.space;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.hub2.admin.AdminStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AdminBasedSpaceStorage extends SpaceStorage {

  /**
   * List of Admin virtual spaces with relevant event handlers required to support event processing
   */
  private final @NotNull AdminStorage adminStorage;

  AdminBasedSpaceStorage(final @NotNull AdminStorage adminStorage) {
    super();
    this.adminStorage = adminStorage;
  }

  /**
   * Initializes the storage, create the transaction table, install needed scripts and extensions.
   */
  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public void initStorage() {
    adminStorage.initStorage();
  }

  /**
   * Starts the maintainer thread that will take about history garbage collection, sequencing and other background jobs.
   */
  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public void startMaintainer() {
    adminStorage.startMaintainer();
  }

  /**
   * Blocking call to perform maintenance tasks right now. One-time maintenance.
   */
  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public void maintainNow() {
    adminStorage.maintainNow();
  }

  /**
   * Stops the maintainer thread.
   */
  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public void stopMaintainer() {
    adminStorage.stopMaintainer();
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull IWriteSession newWriteSession(@Nullable NakshaContext context, boolean useMaster) {
    if (virtualSpaces == null) {
      throw new IllegalStateException("Unable to create new write session: virtual spaces were not initialized");
    }
    if (eventPipelineFactory == null) {
      throw new IllegalStateException(
          "Unable to create new write session: event pipeline factory was not initialized");
    }
    return new AdminBasedSpaceStorageWriter(eventPipelineFactory, adminStorage, virtualSpaces, context, useMaster);
  }

  @Override
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull IReadSession newReadSession(@Nullable NakshaContext context, boolean useMaster) {
    if (virtualSpaces == null) {
      throw new IllegalStateException("Unable to create new write session: virtual spaces were not initialized");
    }
    if (eventPipelineFactory == null) {
      throw new IllegalStateException(
          "Unable to create new write session: event pipeline factory was not initialized");
    }
    return new AdminBasedSpaceStorageReader(eventPipelineFactory, adminStorage, virtualSpaces, context, useMaster);
  }
}
