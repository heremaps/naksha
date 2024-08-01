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

import naksha.model.NakshaFeatureProxy;
import naksha.model.request.ResultRow;
import naksha.model.response.ExecutedOp;

import java.util.ArrayList;
import java.util.List;

public class Sample {

  public static List<ResultRow> sampleXyzResponse(int size) {
    List<ResultRow> returnList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      returnList.add(new ResultRow(ExecutedOp.UPDATED, null, new NakshaFeatureProxy("id" + i)));
    }
    return returnList;
  }
  public static List<ResultRow> sampleXyzWriteResponse(int size, ExecutedOp op) {
    List<ResultRow> returnList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      returnList.add(new ResultRow(op, null, new NakshaFeatureProxy("id" + i)));
    }
    return returnList;
  }
}
