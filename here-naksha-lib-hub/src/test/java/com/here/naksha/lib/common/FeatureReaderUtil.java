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
package com.here.naksha.lib.common;

import static org.junit.jupiter.api.Assertions.fail;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.ReadResult;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.storage.IReadSession;
import java.util.Iterator;

public class FeatureReaderUtil {

  private FeatureReaderUtil() {}

  public static <T> T fetchSingleFeatureFromSpace(
      INaksha hub, NakshaContext nakshaContext, ReadFeatures readFeaturesRequest, Class<T> featureType) {
    try (final IReadSession reader = hub.getSpaceStorage().newReadSession(nakshaContext, false)) {
      final Result result = reader.execute(readFeaturesRequest);
      if (result == null) {
        fail("Storage read result is null!");
      } else if (result instanceof ErrorResult er) {
        fail("Exception reading storages " + er);
      } else if (result instanceof ReadResult<?> rr) {
        Iterator<T> features = rr.withFeatureType(featureType).iterator();
        return features.next();
      } else {
        fail("Unexpected result while reading storages : " + result.getClass());
      }
    }
    throw new RuntimeException("Improve this line"); // TODO: <-- what he says
  }
}
