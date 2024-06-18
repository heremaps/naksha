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
package com.here.naksha.lib.heapcache;

import naksha.model.XyzFeature;
import com.here.naksha.lib.core.util.ILike;
import com.here.naksha.lib.core.util.fib.FibMapEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CacheEntry extends FibMapEntry<String, XyzFeature> {

  /**
   * Create a new entry for a {@link FibSet}.
   *
   * @param key the key.
   */
  public CacheEntry(@NotNull String key) {
    super(key);
  }

  @Override
  public boolean isLike(@Nullable Object key) {
    // Potentially: We could consider other things, for example bounding boxes or alike.
    return ILike.equals(this.key, key);
  }
}
