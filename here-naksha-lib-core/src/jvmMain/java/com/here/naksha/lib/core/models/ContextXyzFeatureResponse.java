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
package com.here.naksha.lib.core.models;


import naksha.base.ListProxy;
import naksha.base.NullableProperty;
import naksha.base.PlatformType;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static naksha.base.Platform.forClass;

//  TODO (CASL-780): this needs to prevail but not here
public class ContextXyzFeatureResponse extends SuccessResponse {
  public static final PlatformType<ContextXyzFeatureResponse> TYPE = forClass(ContextXyzFeatureResponse.class);
  private static final NullableProperty<ContextXyzFeatureResponse, NakshaFeatureList> CONTEXT =
      new NullableProperty<>(NakshaFeatureList.TYPE, "context");
  private static final NullableProperty<ContextXyzFeatureResponse, NakshaFeatureList> VIOLATIONS =
      new NullableProperty<>(NakshaFeatureList.TYPE, "violations");

  /**
   * The list of features to be returned as context
   */
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public @Nullable NakshaFeatureList getContext() {
    return CONTEXT.getValue(this);
  }

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void setContext(@Nullable List<? extends NakshaFeature> contextFeatures) {
    CONTEXT.setValue(this, ListProxy.to(NakshaFeatureList.TYPE, contextFeatures));
  }

  /**
   * The list of violations to be returned as context
   */
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public @Nullable NakshaFeatureList getViolations() {
    return VIOLATIONS.getValue(this);
  }

  @ApiStatus.AvailableSince(NakshaVersion.v2_0_11)
  public void setViolations(@Nullable List<? extends NakshaFeature> violations) {
    VIOLATIONS.setValue(this, ListProxy.to(NakshaFeatureList.TYPE, violations));
  }
}
