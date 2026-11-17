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
package com.here.naksha.lib.view;

import java.util.List;
import java.util.Map;
import java.util.Set;
import naksha.model.request.FeatureTuple;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ViewSuccessResult extends SuccessResponse {

  public Map<String, Integer> originalFeaturesOrder;
  private final Set<String> inheritedFeatureIds;

  public ViewSuccessResult(@NotNull List<FeatureTuple> rows, @Nullable Map<String, Integer> originalFeaturesOrder) {
    this(rows, originalFeaturesOrder, Set.of());
  }

  public ViewSuccessResult(@NotNull List<FeatureTuple> rows, @Nullable Map<String, Integer> originalFeaturesOrder,
      @NotNull Set<String> inheritedFeatureIds) {
    withFeatureTupleList(rows);
    this.originalFeaturesOrder = originalFeaturesOrder;
    this.inheritedFeatureIds = Set.copyOf(inheritedFeatureIds);
  }

  /**
   * Feature IDs present only below the top writable layer in this read snapshot. PATCH-by-ID uses this response-local information for guarded copy-on-write; it is not serialized into the public feature payload.
   * @since 3.0
   */
  public @NotNull Set<String> getInheritedFeatureIds() {
    return inheritedFeatureIds;
  }
}
