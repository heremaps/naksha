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

import java.util.ArrayList;
import java.util.List;
import naksha.base.JvmInt64;
import naksha.model.IStorage;
import naksha.model.Metadata;
import naksha.model.Naksha;
import naksha.model.Tuple;
import naksha.model.TupleNumber;
import naksha.model.Version;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.FeatureTuple;

public class Sample {

  static final TupleNumber tupleNum = new TupleNumber(new JvmInt64(0), 0, 0, 0, Version.fromDouble(3.0), 1);
  static final Metadata metadata = new Metadata(
      tupleNum,
      0,
      null,
      new JvmInt64(0),
      null,
      null,
      null,
      null,
      1,
      0,
      0,
      "sampleTuple",
      "sampleAppId",
      "sampleAuthor",
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
  );

  public static List<FeatureTuple> sampleXyzResponse(int size, IStorage storage) {
    List<FeatureTuple> returnList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      byte[] bytesFeature = Naksha.encodeFeature(new NakshaFeature(), 0, null);
      Tuple tuple = new Tuple(metadata, bytesFeature, null, null, null, null, false);
      returnList.add(new FeatureTuple(tupleNum, tuple));
    }
    return returnList;
  }

  public static List<FeatureTuple> sampleXyzWriteResponse(int size) {
    List<FeatureTuple> returnList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      byte[] bytesFeature = Naksha.encodeFeature(new NakshaFeature(), 0, null);
      Tuple tuple = new Tuple(metadata, bytesFeature, null, null, null, null, false);
      returnList.add(new FeatureTuple(tupleNum, tuple));
    }
    return returnList;
  }
}
