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

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.models.storage.MutableCursor;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.storage.IStorage;

import java.util.Arrays;
import java.util.Comparator;

import static org.mockito.Mockito.mock;

public class MultiStorageViewTest {

  private NakshaContext nakshaContext =
      new NakshaContext().withAppId("VIEW_API_TEST").withAuthor("VIEW_API_AUTHOR");

  void testReadApiNotation() {

    IStorage deltaStorage = mock(IStorage.class);
    IStorage dlbStorage = mock(IStorage.class);
    IStorage consistentStorage = mock(IStorage.class);

    // create multi storage, order of elements is important (first is top)
    MultiStorageView view = new MultiStorageView(deltaStorage, dlbStorage, consistentStorage);

    // to discuss if same context is valid to use across all storages
    ViewReadSession readSession = view.newReadSession(nakshaContext, true);

    ReadFeatures readFeatures = new ReadFeatures("topologies", "buildings");

    // custom merge operation
    MergeOperation customMergeOperation = new CustomMergeOperation();


    readSession.execute(readFeatures, customMergeOperation);
  }

  void testWriteApiNotation() throws NoCursor {

    IStorage deltaStorage = mock(IStorage.class);
    IStorage dlbStorage = mock(IStorage.class);
    IStorage consistentStorage = mock(IStorage.class);

    // create multi storage, order of elements is important (first is top)
    MultiStorageView view = new MultiStorageView(deltaStorage, dlbStorage, consistentStorage);

    // to discuss if same context is valid to use across all storages
    ViewWriteSession writeSession = view.newWriteSession(nakshaContext, true);

    final WriteXyzFeatures request = new WriteXyzFeatures("topologies");
    final XyzFeature feature = new XyzFeature("feature_id_1");
    request.add(EWriteOp.CREATE, feature);

    try (MutableCursor<XyzFeature, XyzFeatureCodec> cursor = writeSession.execute(request).getXyzMutableCursor()) {
      cursor.next();
    } finally {
      writeSession.commit(true);
    }
  }

  class CustomMergeOperation implements MergeOperation {

    @Override
    public FeatureCodec apply(SingleStorageRow[] sameFeatureFromEachStorage) {
      return Arrays.stream(sameFeatureFromEachStorage)
          .sorted(Comparator.comparing(SingleStorageRow::getStoragePriority))
          .findFirst()
          .map(SingleStorageRow::getRow)
          .get();
    }
  }
}
