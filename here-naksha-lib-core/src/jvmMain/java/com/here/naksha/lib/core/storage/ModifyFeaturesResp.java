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
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public class ModifyFeaturesResp {

  private final List<@Nullable NakshaFeature> inserted;
  private final List<@Nullable NakshaFeature> updated;
  private final List<@Nullable NakshaFeature> deleted;

  public ModifyFeaturesResp(
      @NotNull List<@Nullable NakshaFeature> inserted,
      @NotNull List<@Nullable NakshaFeature> updated,
      @NotNull List<@Nullable NakshaFeature> deleted) {
    this.inserted = inserted;
    this.updated = updated;
    this.deleted = deleted;
  }

  public ModifyFeaturesResp() {
    this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

  public List<NakshaFeature> getInserted() {
    return inserted;
  }

  public List<NakshaFeature> getUpdated() {
    return updated;
  }

  public List<NakshaFeature> getDeleted() {
    return deleted;
  }
}
