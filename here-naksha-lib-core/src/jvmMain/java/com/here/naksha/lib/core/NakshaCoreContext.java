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

import naksha.model.NakshaContext;
import org.jetbrains.annotations.NotNull;

import static naksha.base.Platform.forClass;

/**
 * A special context to be used only within the Naksha Java core code.
 */
public class NakshaCoreContext extends NakshaContext {

  // Should be called, ones the application calls `currentContext`
  static {
    NakshaContext.contextType = forClass(NakshaCoreContext.class);
  }

  @Override
  public @NotNull NakshaCoreContext attachToCurrentThread() {
    super.attachToCurrentThread();
    return this;
  }

  // Add own content here
}
