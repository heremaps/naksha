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
package com.here.naksha.lib.core.models.naksha;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.models.PluginCache.getStorageConstructor;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.lambdas.Fe1;
import com.here.naksha.lib.core.models.PluginCache;
import naksha.model.IStorage;
import naksha.model.NakshaVersion;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration of a storage. Storages are internally used to access and modify features and collection.
 */
@AvailableSince(NakshaVersion.v2_0_0)
public class Storage extends Plugin<IStorage> {

  @Deprecated
  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String NUMBER = "number";

  @Deprecated
  public long getNumber() {
    return (Long) getRaw(NUMBER);
  }

  @Deprecated
  public void setNumber(long number) {
    setRaw(NUMBER, number);
  }

  /**
   * Do not use anymore, please call {@link PluginCache#getStorageConstructor(String, Class)} and create the instance yourself.
   */
  @Deprecated
  @Override
  public @NotNull IStorage newInstance(@NotNull INaksha naksha) {
    Fe1<IStorage, Storage> constructor =
        getStorageConstructor("com.here.naksha.lib.psql.PsqlStorage", Storage.class);
    try {
      return constructor.call(null);
    } catch (Exception e) {
      throw unchecked(e);
    }
  }
}
