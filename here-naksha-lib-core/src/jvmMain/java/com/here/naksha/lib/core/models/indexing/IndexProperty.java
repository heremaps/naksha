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

import naksha.base.JvmBoxingUtil;
import naksha.base.JvmListProxy;
import naksha.model.objects.NakshaFeature;

public class IndexProperty extends NakshaFeature {

  private static final String PATH = "path";
  private static final String NULLS = "nulls";
  private static final String ASC = "asc";

  /**
   * The JSON path to the property to index.
   */
  public String getPath() {
    return (String) getRaw(PATH);
  }

  public void setPath(String path) {
    setRaw(PATH, path);
  }

  /**
   * If the property should be naturally ordered ascending.
   */
  public boolean isAsc() {
    return getOrSet(ASC, true);
  }

  public void setAsc(boolean asc) {
    setRaw(ASC, asc);
  }

  /**
   * Optionally decide if {@code null} values should be ordered first or last. If not explicitly defined, automatically decided.
   */
  public Nulls getNulls() {
    return JvmBoxingUtil.box(get(NULLS), Nulls.class);
  }

  public void setNulls(Nulls nulls) {
    setRaw(NULLS, nulls);
  }

  public enum Nulls {
    FIRST,
    LAST
  }

  public static class IndexProperties extends JvmListProxy<IndexProperty> {
    public IndexProperties() {
      super(IndexProperty.class);
    }
  }
}
