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

import naksha.base.JvmListProxy;
import naksha.base.JvmMapProxy;
import naksha.model.objects.NakshaFeature;

/** Base class of all possible constraints that can be combined. */
public class Constraint extends NakshaFeature {

  public static class ConstraintList extends JvmListProxy<Constraint> {

    public ConstraintList() {
      super(Constraint.class);
    }
  }

  public static class ConstraintMap extends JvmMapProxy<String, Constraint> {

    public ConstraintMap() {
      super(String.class, Constraint.class);
    }
  }
}
