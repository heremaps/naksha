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
package com.here.naksha.lib.core.models.indexing;

import java.util.List;
import naksha.base.JvmBoxingUtil;

public class ConstraintNot extends Constraint {

  private static final String OF = "of";

  /**
   * The constraints that should be negated.
   */
  public List<Constraint> getOf() {
    return JvmBoxingUtil.box(get(OF), ConstraintList.class);
  }

  public void setOf(List<Constraint> of) {
    ConstraintList proxyBasedConstrains = new ConstraintList();
    proxyBasedConstrains.addAll(of);
    setRaw(OF, proxyBasedConstrains);
  }
}
