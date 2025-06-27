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

import naksha.base.*;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;

import static naksha.base.NakshaBaseKt.Boolean_TYPE;
import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

public class IndexProperty extends NakshaFeature {
  public static final PlatformType<IndexProperty> TYPE = forClass(IndexProperty.class);
  private static final NullableProperty<IndexProperty, String> PATH
      = new NullableProperty<>(String_TYPE, "path");
  private static final NotNullEnum<IndexProperty, Nulls> NULLS
      = new NotNullEnum<>(Nulls.TYPE);
  private static final NotNullProperty<IndexProperty, Boolean> ASC
      = new NotNullProperty<>(Boolean_TYPE, "asc", (self, name) -> Boolean.TRUE);

  /**
   * The JSON path to the property to index.
   */
  public String getPath() {return PATH.getValue(this); }
  public void setPath(String path) { PATH.setValue(this, path); }

  /**
   * If the property should be naturally ordered ascending.
   */
  public boolean isAsc() { return ASC.getValue(this); }
  public void setAsc(boolean asc) { ASC.setValue(this, asc); }

  /**
   * Optionally decide if {@code null} values should be ordered first or last. If not explicitly defined, automatically decided.
   */
  public @NotNull Nulls getNulls() { return NULLS.getValue(this); }

  public void setNulls(Nulls nulls) { NULLS.setValue(this, nulls); }

  public static class Nulls extends PlatformEnum {
    static final PlatformType<Nulls>  TYPE = forClass(Nulls.class);

    static final Nulls FIRST = defIgnoreCase(TYPE, "FIRST");
    static final Nulls LAST = defIgnoreCase(TYPE, "LAST");

    @Override
    public @NotNull PlatformType<? extends PlatformEnum> namespace() {
      return TYPE;
    }
  }
}
