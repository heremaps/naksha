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

import naksha.model.Action;
import naksha.model.IStorage;
import naksha.model.objects.XyzMembers;
import naksha.model.request.FeatureTuple;
import naksha.model.request.FeatureTupleList;

import static naksha.model.RandomFeatures.randomFeature;

public class Sample {

  public static FeatureTupleList sampleXyzResponse(int size, IStorage storage) {
    FeatureTupleList returnList = new FeatureTupleList();
    for (int i = 0; i < size; i++) {
      returnList.add(new FeatureTuple(randomFeature(Integer.toString(i)), XyzMembers.XyzTn));
    }
    return returnList;
  }

  public static FeatureTupleList sampleXyzWriteResponse(int size, Action action) {
    final FeatureTupleList returnList = new FeatureTupleList();
    for (int i = 0; i < size; i++) {
      returnList.add(new FeatureTuple(randomFeature(Integer.toString(i), (f) -> {
        f.getProperties().getXyz().setRaw("action", action.toString());
        return f;
      }), XyzMembers.XyzTn));
    }
    return returnList;
  }
}
