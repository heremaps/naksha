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

import naksha.base.AnyObject;
import naksha.base.ListProxy;
import naksha.base.MapProxy;
import naksha.base.PlatformType;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

/** Base class of all possible constraints that can be combined. */
public class Constraint extends AnyObject {
  public static final PlatformType<Constraint> TYPE = forClass(Constraint.class);

  public static class ConstraintList extends ListProxy<Constraint> {
    public static final PlatformType<ConstraintList> TYPE = forClass(ConstraintList.class);

    public ConstraintList() {
      super(Constraint.TYPE);
    }
  }

  public static class ConstraintMap extends MapProxy<String, Constraint> {
    public static final PlatformType<ConstraintMap> TYPE = forClass(ConstraintMap.class);

    public ConstraintMap() {
      super(String_TYPE, Constraint.TYPE);
    }
  }
}
