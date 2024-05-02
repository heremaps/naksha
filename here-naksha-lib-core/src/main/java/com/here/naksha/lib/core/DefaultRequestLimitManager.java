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
package com.here.naksha.lib.core;

public class DefaultRequestLimitManager implements IRequestLimitManager {
  private final int instanceLevelLimit;
  private final double actorLimitPct;

  private static int getAvailableProcessors() {
    return Runtime.getRuntime().availableProcessors();
  }

  // This function is useful where Hub is not involved
  public DefaultRequestLimitManager() {
    this.instanceLevelLimit = 30 * getAvailableProcessors();
    this.actorLimitPct = 25; // 25%
  }

  public DefaultRequestLimitManager(int cpuLevelLimit, int actorLimitPct) {
    this.instanceLevelLimit = cpuLevelLimit * getAvailableProcessors();
    this.actorLimitPct = actorLimitPct;
  }

  @Override
  public long getInstanceLevelLimit() {
    return instanceLevelLimit;
  }

  @Override
  public long getActorLevelLimit(NakshaContext context) {
    return (long) ((instanceLevelLimit * actorLimitPct) / 100);
  }
}
