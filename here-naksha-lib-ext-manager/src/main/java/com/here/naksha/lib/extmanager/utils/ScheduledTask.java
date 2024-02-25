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
package com.here.naksha.lib.extmanager.utils;

import com.here.naksha.lib.core.lambdas.Fe;
import com.here.naksha.lib.core.lambdas.Fe0;
import com.here.naksha.lib.extmanager.ExtConfig;
import com.here.naksha.lib.extmanager.ExtensionCache;
import com.here.naksha.lib.extmanager.models.ExtensionMetaData;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScheduledTask implements Runnable {

  private ExtensionCache extensionCache;
  protected @Nullable Fe lambda;

  @NotNull
  ExtConfig extConfig;

  public ScheduledTask(
      ExtensionCache extensionCache, @NotNull Fe0<List<ExtensionMetaData>> lambda, ExtConfig extConfig) {
    this.extensionCache = extensionCache;
    this.lambda = lambda;
    this.extConfig = extConfig;
  }

  @Override
  public void run() {
    if (!this.extensionCache.getLock()) {
      System.out.println("Already in progress, Exiting.");
      return;
    }
    System.out.println("Refreshing loader cache " + Thread.currentThread().getName());
    List<ExtensionMetaData> result = new ArrayList<>();
    try {
      if (lambda instanceof Fe0) {
        try {
          result = ((Fe0<List<ExtensionMetaData>>) lambda).call();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      this.extensionCache.buildExtensionCache(result, extConfig);
    } finally {
      this.extensionCache.unlock();
      System.out.println(
          "Refreshing loader completed " + Thread.currentThread().getName());
    }
  }
}
