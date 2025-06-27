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

import naksha.base.ListProxy;
import naksha.base.NullableProperty;
import naksha.base.PlatformType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static naksha.base.Platform.forClass;

public class ConstraintOne extends Constraint {
  public static final PlatformType<ConstraintOne> TYPE = forClass(ConstraintOne.class);
  private static final NullableProperty<ConstraintOne, ConstraintList> OF
      = new NullableProperty<>(ConstraintList.TYPE, "of");

  /**
   * The constraints of which at least one need to hold true (OR).
   */
  public @Nullable ConstraintList getOf() {
    return OF.getValue(this);
  }
  public void setOf(@Nullable List<Constraint> of) {
    OF.setValue(this, ListProxy.to(ConstraintList.TYPE, of));
  }
}