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
package com.here.naksha.lib.core.models.storage;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Helper to simplify the usage of standard {@link XyzFeature}'s as provided by normal users.
 *
 * @param <T> The concrete type of the feature.
 */
public class WriteXyzFeatures<T extends XyzFeature> extends WriteFeatures<T> {

  public WriteXyzFeatures(@NotNull String collectionId) {
    super(collectionId);
  }

  public WriteXyzFeatures(@NotNull String collectionId, @NotNull List<WriteOp<T>> modifies) {
    super(collectionId, modifies);
  }
}
