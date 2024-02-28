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

import com.here.naksha.lib.core.lambdas.Fe;
import com.here.naksha.lib.core.lambdas.Fe0;
import com.here.naksha.lib.core.models.features.ExtensionConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledTask implements Runnable {
  private static final @NotNull Logger logger = LoggerFactory.getLogger(ExtensionCache.class);
  private ExtensionCache extensionCache;
  protected @Nullable Fe lambda;

  public ScheduledTask(@NotNull ExtensionCache extensionCache, @NotNull Fe0<ExtensionConfig> lambda) {
    this.extensionCache = extensionCache;
    this.lambda = lambda;
  }

  @Override
  public void run() {
    while (true) {
      System.out.println("Refreshing loader cache task starts");
      ExtensionConfig extensionConfig = null;
      try {
        if (lambda instanceof Fe0) {
          extensionConfig = ((Fe0<ExtensionConfig>) lambda).call();
        }
        this.extensionCache.buildExtensionCache(extensionConfig);
      } catch (Exception e) {
        logger.error("Failed to refresh extension cache.", e);
      } finally {
        logger.info("Refresh loader task completed");
      }
      try {
        System.out.println("Going to sleep for " + extensionConfig.getExpiry() + " Seconds");
        Thread.sleep(extensionConfig.getExpiry());
      } catch (InterruptedException e) {
        logger.error("Refresh task is interrupted");
      }
    }
  }
}
