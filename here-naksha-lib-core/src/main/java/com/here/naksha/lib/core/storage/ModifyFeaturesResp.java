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

import java.util.ArrayList;
import java.util.List;
import naksha.model.NakshaFeatureProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public class ModifyFeaturesResp {

  private final List<@Nullable NakshaFeatureProxy> inserted;
  private final List<@Nullable NakshaFeatureProxy> updated;
  private final List<@Nullable NakshaFeatureProxy> deleted;

  public ModifyFeaturesResp(
      @NotNull List<@Nullable NakshaFeatureProxy> inserted,
      @NotNull List<@Nullable NakshaFeatureProxy> updated,
      @NotNull List<@Nullable NakshaFeatureProxy> deleted) {
    this.inserted = inserted;
    this.updated = updated;
    this.deleted = deleted;
  }

  public ModifyFeaturesResp() {
    this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

  public List<NakshaFeatureProxy> getInserted() {
    return inserted;
  }

  public List<NakshaFeatureProxy> getUpdated() {
    return updated;
  }

  public List<NakshaFeatureProxy> getDeleted() {
    return deleted;
  }
}
