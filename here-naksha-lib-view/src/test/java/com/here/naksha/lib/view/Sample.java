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

import naksha.base.JvmInt64;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ExecutedOp;
import naksha.model.request.ResultTuple;
import naksha.psql.PgUtil;

import java.util.ArrayList;
import java.util.List;

public class Sample {

  static final TupleNumber tupleNum = new TupleNumber(new JvmInt64(0), Version.fromDouble(3.0),0);
  static final Metadata metadata = new Metadata(
          tupleNum.storeNumber,
          tupleNum.storeNumber,
          tupleNum.storeNumber,
          tupleNum.storeNumber,
          null,
          tupleNum.version,
          null,
          tupleNum.uid,
          null,
          0,
          1,
          0,
          0,
          "sampleTuple",
          "sampleAppId",
          "sampleAuthor",
          null,
          null
          );

  public static List<ResultTuple> sampleXyzResponse(int size, IStorage storage) {
    List<ResultTuple> returnList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      byte[] bytesFeature = PgUtil.encodeFeature(new NakshaFeature(), 0, null);
      Tuple tuple = new Tuple(storage, tupleNum, metadata, bytesFeature, null, null, null, null);
      returnList.add(new ResultTuple(storage, tupleNum, ExecutedOp.READ,"id" + i, tuple));
    }
    return returnList;
  }
  public static List<ResultTuple> sampleXyzWriteResponse(int size, IStorage storage, ExecutedOp op) {
    List<ResultTuple> returnList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      byte[] bytesFeature = PgUtil.encodeFeature(new NakshaFeature(), 0, null);
      Tuple tuple = new Tuple(storage, tupleNum, metadata, bytesFeature, null, null, null, null);
      returnList.add(new ResultTuple(storage, tupleNum, op,"id" + i, tuple));
    }
    return returnList;
  }
}
