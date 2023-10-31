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
package com.here.naksha.lib.hub2.admin;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.view.ViewDeserialize;
import com.here.naksha.lib.psql.PsqlConfig;
import com.here.naksha.lib.psql.PsqlStorage;
import org.jetbrains.annotations.NotNull;

final class PsqlAdminStorage extends AdminStorage {

  private static final String DEFAULT_STORAGE_CONFIG_PATH = "config/default-storage.json";
  private static final String STORAGE_ID = "naksha-admin-db";

  PsqlAdminStorage(@NotNull PsqlConfig psqlConfig, @NotNull NakshaContext nakshaContext) {
    super(new PsqlStorage(psqlConfig, STORAGE_ID), nakshaContext);
  }

  @Override
  protected Storage fetchDefaultStorage() {
    try (final Json json = Json.get()) {
      final String storageJson = IoHelp.readResource(DEFAULT_STORAGE_CONFIG_PATH);
      return json.reader(ViewDeserialize.Storage.class)
          .forType(Storage.class)
          .readValue(storageJson);
    } catch (Exception e) {
      throw unchecked(new Exception("Unable to read default Storage file. " + e.getMessage(), e));
    }
  }
}
