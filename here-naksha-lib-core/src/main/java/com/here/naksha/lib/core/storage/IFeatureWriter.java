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
package com.here.naksha.lib.core.storage;

import static naksha.model.NakshaVersion.v2_0_5;

import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * Interface to grant write-access to features in a collection.
 *
 * @param <FEATURE> the feature-type to modify.
 */
@Deprecated
@AvailableSince(v2_0_5)
public interface IFeatureWriter<FEATURE extends NakshaFeature> extends IFeatureReader<FEATURE> {

  /**
   * Perform the given operations as bulk operation and return the results.
   *
   * @param req the modification request.
   * @return the modification result with the features that have been inserted, update and deleted.
   */
  @AvailableSince(v2_0_5)
  @NotNull
  ModifyFeaturesResp modifyFeatures(@NotNull ModifyFeaturesReq<FEATURE> req);
}
