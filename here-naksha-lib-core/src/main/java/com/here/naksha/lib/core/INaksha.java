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
package com.here.naksha.lib.core;

import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.storage.IStorage;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Naksha host interface. When an application bootstraps, it creates a Naksha host implementation and exposes it via the
 * {@link #instance} reference. The reference implementation is based upon the PostgresQL database, but alternative implementations are
 * possible, for example the Naksha extension library will fake a Naksha-Hub.
 */
@SuppressWarnings("unused")
public interface INaksha {

  /**
   * The reference to the Naksha implementation provided by the host. Rather use the {@link #get()} method to get the instance.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  AtomicReference<@Nullable INaksha> instance = new AtomicReference<>();

  /**
   * Returns the reference to the Naksha implementation provided by the host.
   *
   * @return the reference to the Naksha implementation provided by the host.
   * @throws NullPointerException if the Naksha interface is not available (no host registered).
   */
  static @NotNull INaksha get() {
    final INaksha hub = instance.getPlain();
    if (hub == null) {
      throw new NullPointerException();
    }
    return hub;
  }

  /**
   * Create a new task for the given event.
   *
   * @param eventClass the class of the event-type to create a task for.
   * @param <EVENT>    the event-type.
   * @return The created task.
   * @throws XyzErrorException If the creation of the task failed for some reason.
   */
  <EVENT extends Event, TASK extends AbstractTask<EVENT>> @NotNull TASK newEventTask(
      @NotNull Class<EVENT> eventClass);

  /**
   * Returns the administration storage that is guaranteed to have all the {@link NakshaAdminCollection admin collections}. This storage does
   * have the storage number {@link NakshaAdminCollection#ADMIN_DB_NUMBER}.
   *
   * @return the administration storage.
   */
  @NotNull
  IStorage adminStorage();
}
