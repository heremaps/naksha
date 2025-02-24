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

import java.util.List;
import org.jetbrains.annotations.NotNull;

//  TODO (CASL-780): this needs to prevail but not here
/**
 * All well-known collections of the Naksha-Hub itself. Still, not all Naksha-Hubs may support them, for example the Naksha extension
 * library currently does not support any collection out of the box!
 */
public final class HubInternalIdentifiers {

  /**
   * The map id (schema name) used by Naksha Hub Admin
   */
  public static final String HUB_INTERNAL_MAP_ID = "hub_internal_map";

  /**
   * The id of storage to be used by Naksha Hub Admin
   */
  public static final String HUB_INTERNAL_STORAGE_ID = "hub_internal_storage";

  /**
   * The Naksha-Hub configurations.
   */
  public static final String CONFIGS = "hub_internal:configs";

  /**
   * The collections for all spaces.
   */
  public static final String SPACES = "hub_internal:spaces";

  /**
   * The collections for all subscriptions.
   */
  public static final String SUBSCRIPTIONS = "hub_internal:subscriptions";

  /**
   * The collections for all connectors.
   */
  public static final String EVENT_HANDLERS = "hub_internal:event_handlers";

  /**
   * The collections for all storages.
   */
  public static final String STORAGES = "hub_internal:storages";

  /**
   * The collections for all extensions.
   */
  public static final String EXTENSIONS = "hub_internal:extensions";

  /**
   * List of all admin-db collections.
   */
  public static final List<@NotNull String> ALL_HUB_INTERNAL_COLLECTIONS =
      List.of(CONFIGS, SPACES, SUBSCRIPTIONS, EVENT_HANDLERS, STORAGES, EXTENSIONS);
}
