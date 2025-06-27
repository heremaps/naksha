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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import naksha.base.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.*;
import static naksha.base.Platform.forClass;

/** A condition to check. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "Check")
public class ConstraintCheck extends Constraint {
  public static final PlatformType<ConstraintCheck> TYPE = forClass(ConstraintCheck.class);

  private static final NotNullProperty<ConstraintCheck, ConstraintTest> TEST
      = new NotNullProperty<>(ConstraintTest.TYPE, "test");
  private static final NotNullProperty<ConstraintCheck, String> PATH
      = new NotNullProperty<>(String_TYPE, "path");
  private static final NullableProperty<ConstraintCheck, Object> VALUE
      = new NullableProperty<>(Object_TYPE, "value");

  /** The check to perform. */
  public @NotNull ConstraintTest getTest() { return TEST.getValue(this); }
  public void setTest(@NotNull ConstraintTest test) { TEST.setValue(this, test); }

  /** The JSON path to the property to check. */
  public @NotNull String getPath() { return PATH.getValue(this); }
  public void setPath(@NotNull String path) { PATH.setValue(this, path); }

  /** The optional value for the check. */
  public @Nullable Object getValue() { return VALUE.getValue(this); }
  public void setValue(@Nullable Object value) { VALUE.setValue(this, value); }
}
