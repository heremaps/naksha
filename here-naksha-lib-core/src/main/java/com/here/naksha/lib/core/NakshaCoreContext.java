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

import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import naksha.base.fn.Fn0;
import naksha.base.fn.Fn1;
import naksha.base.fn.Fx1;
import naksha.model.NakshaContext;
import naksha.model.NakshaVersion;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * A special context to be used only within the Naksha Java core code.
 */
public class NakshaCoreContext extends NakshaContext {
  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

  @SuppressWarnings("NotNullFieldNotInitialized")
  private static @NotNull Fn0<NakshaContext> _super;

  /**
   * Must be invoked when the application using the core-library starts to bind this context as default context.
   */
  public static void init() {
    if (INITIALIZED.compareAndSet(false, true)) {
      NakshaContext.setConstructorRef(NakshaCoreContext::new);
      _super = NakshaCoreContext::currentContext;
      NakshaContext.setCurrentRef(NakshaCoreContext::currentContext);
    }
  }

  static {
    init();
  }

  protected NakshaCoreContext() {}

  /**
   * Returns the current core-context. The method will return the same as {@link NakshaContext#currentContext()}.
   * @return The current core-context.
   */
  @AvailableSince(NakshaVersion.v2_0_5)
  public static @NotNull NakshaCoreContext currentContext() {
    final AbstractTask<?, ?> task = AbstractTask.currentTask();
    return task != null ? (NakshaCoreContext) task.context() : (NakshaCoreContext) _super.call();
  }
}
