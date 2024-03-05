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
package com.here.naksha.lib.extmanager;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.ExtensionConfig;
import com.here.naksha.lib.core.models.features.Extension;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ExtensionManager implements IExtensionManager {
  private final @NotNull INaksha naksha;
  private final ExtensionCache extensionCache;
  private Thread refreshTask;

  public ExtensionManager(@NotNull INaksha naksha) {
    this.naksha = naksha;
    this.extensionCache = new ExtensionCache(naksha);
    this.buildExtensionMap();
    this.scheduleRefreshCache();
  }

  private void buildExtensionMap() {
    ExtensionConfig extensionConfig = naksha.getExtensionConfig();
    extensionCache.buildExtensionCache(extensionConfig);
  }

  /**
   * get Isolation Class loader for given extension Id
   * @param extensionId
   * @return
   */
  @Override
  public ClassLoader getClassLoader(@NotNull String extensionId) {
    return this.extensionCache.getClassLoaderById(extensionId);
  }

  private void scheduleRefreshCache() {
    ScheduledTask scheduledTask = new ScheduledTask(this.extensionCache, () -> naksha.getExtensionConfig());
    refreshTask = new Thread(scheduledTask);
    refreshTask.start();
  }

  /**
   * Fetch registered extensions cached in extension manager
   * @return List {@link Extension} list of extensions
   */
  public List<Extension> getCachedExtensions() {
    return this.extensionCache.getCachedExtensions();
  }
}
