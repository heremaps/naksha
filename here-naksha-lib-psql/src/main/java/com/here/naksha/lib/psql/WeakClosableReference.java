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
package com.here.naksha.lib.psql;

import java.lang.ref.WeakReference;

/**
 * A weak reference that should be closed, but when not done, the closing through the garbage collector can be detected and resources can be
 * released.
 *
 * @param <T> The referent type.
 */
class WeakClosableReference<T> extends WeakReference<T> {

  WeakClosableReference(T referent) {
    super(referent);
  }

  void close() {
    isClosed = true;
  }

  private boolean isClosed;

  boolean isClosed() {
    return isClosed || get() == null;
  }
}
