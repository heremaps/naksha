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
package com.here.naksha.lib.handlers.val;

import java.util.List;
import naksha.base.JvmProxyUtil;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContextXyzFeatureResult extends SuccessResponse {

  private static final String CONTEXT_KEY = "context";
  private static final String VIOLATIONS_KEY = "violations";

  public void setFeatures(@NotNull List<NakshaFeature> nakshaFeatures) {
    super.setFeatures(NakshaFeatureList.fromList(nakshaFeatures));
  }

  /**
   * The list of features to be returned as context
   */
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public @Nullable List<NakshaFeature> getContext() {
    return JvmProxyUtil.box(get(CONTEXT_KEY), NakshaFeatureList.class);
  }

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void setContext(@Nullable List<NakshaFeature> contextFeatures) {
    setContext(NakshaFeatureList.fromList(contextFeatures));
  }

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void setContext(@Nullable NakshaFeatureList contextFeatures) {
    put(CONTEXT_KEY, contextFeatures);
  }

  /**
   * The list of violations to be returned as context
   */
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public @Nullable List<NakshaFeature> getViolations() {
    return JvmProxyUtil.box(get(CONTEXT_KEY), NakshaFeatureList.class);
  }

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void setViolations(@Nullable List<NakshaFeature> violations) {
    setViolations(NakshaFeatureList.fromList(violations));
  }

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void setViolations(@Nullable NakshaFeatureList violations) {
    put(VIOLATIONS_KEY, violations);
  }
}
