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
package com.here.naksha.lib.hub.mock;

import java.util.List;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;

public class MockResult<T extends NakshaFeature> extends SuccessResponse {

  public static <T extends NakshaFeature> MockResult<T> mockResultWithFeature(@NotNull T feature){
    return mockResultWithFeatures(List.of(feature));
  }

  public static <T extends NakshaFeature> MockResult<T> mockResultWithFeatures(@NotNull List<T> features){
    MockResult<T> result = new MockResult<T>();
    result.setFeatures(features);
    return result;
  }
}
