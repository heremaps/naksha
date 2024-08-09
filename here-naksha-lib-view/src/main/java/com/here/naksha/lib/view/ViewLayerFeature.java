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
package com.here.naksha.lib.view;

import naksha.model.objects.NakshaFeature;

public class ViewLayerFeature {

  private final NakshaFeature feature;

  // priority 0 - is highest
  private final int storagePriority;

  private final ViewLayer viewLayerRef;

  public ViewLayerFeature(NakshaFeature feature, int storagePriority, ViewLayer viewLayerRef) {
    this.feature = feature;
    this.storagePriority = storagePriority;
    this.viewLayerRef = viewLayerRef;
  }

  public int getStoragePriority() {
    return storagePriority;
  }

  public ViewLayer getViewLayerRef() {
    return viewLayerRef;
  }

  public NakshaFeature getFeature() {
    return feature;
  }
}
