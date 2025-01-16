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

import com.here.naksha.lib.core.INaksha;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;

public abstract class Plugin<API> extends NakshaFeature {

  public static final String CLASS_NAME = "className";

  public @NotNull String getClassName() {
    return (String) getRaw(CLASS_NAME);
  }

  public void setClassName(@NotNull String className) {
    setRaw(CLASS_NAME, className);
  }

  /**
   * Create a new instance of the plugin.
   *
   * @param naksha the reference to the Naksha-Hub that wants to have the instance.
   * @return the API implementing the plugin.
   */
  public abstract @NotNull API newInstance(@NotNull INaksha naksha);
}
