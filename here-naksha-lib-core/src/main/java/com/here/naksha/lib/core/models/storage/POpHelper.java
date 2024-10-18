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
package com.here.naksha.lib.core.models.storage;

import com.here.naksha.lib.core.NakshaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class POpHelper {

  /**
   * Returns features that are best matches for given txn.
   * Means that either feature has exact txn, or it's the last version of feature before given txn.
   * !Important: set readAllVersions(true) with this search.
   *
   * @param value
   * @return
   */
  @ApiStatus.AvailableSince(NakshaVersion.v2_0_17)
  public static @NotNull POp closestTxnNotGreaterThan(@NotNull Number value) {
    return POp.and(POp.lte(PRef.txn(), value), POp.or(POp.gt(PRef.txn_next(), value), POp.eq(PRef.txn_next(), 0)));
  }
}