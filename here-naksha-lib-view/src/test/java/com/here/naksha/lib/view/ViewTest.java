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
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.storage.IStorage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Comparator;

import static org.mockito.Mockito.mock;

public class ViewTest {

  private NakshaContext nakshaContext =
      new NakshaContext().withAppId("VIEW_API_TEST").withAuthor("VIEW_API_AUTHOR");

  void testReadApiNotation() {

    ViewLayer topologiesDS = new ViewLayer(mock(IStorage.class), "topologies");
    ViewLayer buildingsDS = new ViewLayer(mock(IStorage.class), "buildings");
    ViewLayer topologiesCS = new ViewLayer(mock(IStorage.class), "topologies");

    ViewCollection viewCollection = new ViewCollection("myCollection", topologiesDS, buildingsDS, topologiesCS);

    View view = new View(viewCollection);

    // to discuss if same context is valid to use across all storages
    ViewReadSession readSession = view.newReadSession(nakshaContext, true);

    ViewReadFeaturesRequest readFeatures = new ViewReadFeaturesRequest();

    // custom merge operation
    MergeOperation customMergeOperation = new CustomMergeOperation();

    // custom fetch ids resolver
    MissingIdResolver skipFetchingResolver = new SkipFetchingMissing();

    readSession.execute(readFeatures, customMergeOperation, skipFetchingResolver);
  }

  void testWriteApiNotation() throws NoCursor {

    ViewLayer topologiesDS = new ViewLayer(mock(IStorage.class), "topologies");
    ViewLayer buildingsDS = new ViewLayer(mock(IStorage.class), "buildings");
    ViewLayer topologiesCS = new ViewLayer(mock(IStorage.class), "topologies");;

    ViewCollection viewCollection = new ViewCollection("myCollection", topologiesDS, buildingsDS, topologiesCS);

    View view = new View(viewCollection);

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

  class SkipFetchingMissing implements MissingIdResolver {

    @Override
    public boolean skip() {
      return true;
    }

    @Override
    public Pair<ViewLayer, String> idsToSearch(ViewLayerRow[] multipleResults) {
      return null;
    }
  }

  class CustomMergeOperation implements MergeOperation {

    @Override
    public FeatureCodec apply(ViewLayerRow[] sameFeatureFromEachStorage) {
      return Arrays.stream(sameFeatureFromEachStorage)
          .sorted(Comparator.comparing(ViewLayerRow::getStoragePriority))
          .findFirst()
          .map(ViewLayerRow::getRow)
          .get();
    }
  }
}
