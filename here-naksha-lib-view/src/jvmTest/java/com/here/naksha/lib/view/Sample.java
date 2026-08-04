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
package com.here.naksha.lib.view;

import static naksha.base.LibBaseKt.Int64;
import static naksha.model.RandomFeatures.randomFeature;

import naksha.base.Action;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.Tuple;
import naksha.base.TupleNumber;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;
import naksha.model.request.FeatureTupleList;

public class Sample {

  public static FeatureTuple featureTuple(NakshaFeature feature) {
    TupleNumber tupleNumber = new TupleNumber(Int64(1), 0, 0, Naksha.featureNumber(feature.getId()), Int64(1));
    feature.getProperties().getXyz().setRaw("uuid", tupleNumber.toString());
    FeatureTuple featureTuple = new FeatureTuple(tupleNumber, (Tuple) null);
    featureTuple.setCachedFeature(feature);
    return featureTuple;
  }

  public static FeatureTupleList sampleXyzResponse(int size, IStorage storage) {
    FeatureTupleList returnList = new FeatureTupleList();
    for (int i = 0; i < size; i++) {
      returnList.add(featureTuple(randomFeature(Integer.toString(i))));
    }
    return returnList;
  }

  public static FeatureTupleList sampleXyzWriteResponse(int size, Action action) {
    final FeatureTupleList returnList = new FeatureTupleList();
    for (int i = 0; i < size; i++) {
      returnList.add(featureTuple(randomFeature(Integer.toString(i), (f) -> {
        f.getProperties().getXyz().setRaw("action", action.toString());
        return f;
      })));
    }
    return returnList;
  }
}
