/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.handler.activitylog;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.EExecutedOp;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.ListBasedForwardCursor;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodecFactory;
import java.util.List;

public class ActivityHistorySuccessResult extends SuccessResult {

  private final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor;

  private ActivityHistorySuccessResult(ForwardCursor<XyzFeature, XyzFeatureCodec> cursor) {
    this.cursor = cursor;
  }

  public ForwardCursor<XyzFeature, XyzFeatureCodec> getCursor() {
    return cursor;
  }

  static ActivityHistorySuccessResult forFeatures(List<XyzFeature> features) {
    XyzFeatureCodecFactory codecFactory = XyzFeatureCodecFactory.get();
    List<XyzFeatureCodec> codecs = features.stream()
        .map(feature -> featureCodec(codecFactory, feature))
        .toList();
    return new ActivityHistorySuccessResult(new ListBasedForwardCursor<>(codecFactory, codecs));
  }

  private static XyzFeatureCodec featureCodec(XyzFeatureCodecFactory codecFactory, XyzFeature feature) {
    return codecFactory.newInstance().withOp(EExecutedOp.READ).withFeature(feature);
  }
}
