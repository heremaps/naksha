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

import static com.here.naksha.lib.view.Sample.sampleXyzResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.MutableCursor;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodecFactory;
import com.here.naksha.lib.core.storage.IStorage;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class ViewTest {

  private NakshaContext nakshaContext =
      new NakshaContext().withAppId("VIEW_API_TEST").withAuthor("VIEW_API_AUTHOR");

  @Test
  void testReadApiNotation() throws NoCursor {

    // given
    IStorage storage = mock(IStorage.class);
    ViewLayer topologiesDS = new ViewLayer(storage, "topologies");
    ViewLayer buildingsDS = new ViewLayer(storage, "buildings");
    ViewLayer topologiesCS = new ViewLayer(storage, "topologies");

    // each layer is going to return 3 same records
    List<XyzFeatureCodec> results = sampleXyzResponse(3);
    when(storage.newReadSession(nakshaContext, false)).thenReturn(new MockReadSession(results));

    ViewCollection viewCollection = new ViewCollection("myCollection", topologiesDS, buildingsDS, topologiesCS);

    View view = new View(viewCollection);

    MergeOperation<XyzFeature, XyzFeatureCodec> customMergeOperation = new CustomMergeOperation();
    MissingIdResolver<XyzFeature, XyzFeatureCodec> skipFetchingResolver = new SkipFetchingMissing();

    // when
    ViewReadSession readSession = view.newReadSession(nakshaContext, false);
    ViewReadFeaturesRequest readFeatures = new ViewReadFeaturesRequest();
    Result result = readSession.execute(
        readFeatures, XyzFeatureCodecFactory.get(), customMergeOperation, skipFetchingResolver);
    MutableCursor<XyzFeature, XyzFeatureCodec> cursor = result.getXyzMutableCursor();

    // then
    assertTrue(cursor.next());
    List<XyzFeatureCodec> allFeatures = cursor.asList();
    assertEquals(3, allFeatures.size());
    assertTrue(allFeatures.containsAll(results));
  }

  void testWriteApiNotation() throws NoCursor {

    ViewLayer topologiesDS = new ViewLayer(mock(IStorage.class), "topologies");
    ViewLayer buildingsDS = new ViewLayer(mock(IStorage.class), "buildings");
    ViewLayer topologiesCS = new ViewLayer(mock(IStorage.class), "topologies");
    ;

    ViewCollection viewCollection = new ViewCollection("myCollection", topologiesDS, buildingsDS, topologiesCS);

    View view = new View(viewCollection);

    // to discuss if same context is valid to use across all storages
    ViewWriteSession writeSession = view.newWriteSession(nakshaContext, true);

    final WriteXyzFeatures request = new WriteXyzFeatures("topologies");
    final XyzFeature feature = new XyzFeature("feature_id_1");
    request.add(EWriteOp.CREATE, feature);

    try (MutableCursor<XyzFeature, XyzFeatureCodec> cursor =
        writeSession.execute(request).getXyzMutableCursor()) {
      cursor.next();
    } finally {
      writeSession.commit(true);
    }
  }

  static class SkipFetchingMissing implements MissingIdResolver<XyzFeature, XyzFeatureCodec> {

    @Override
    public boolean skip() {
      return true;
    }

    @Override
    public Pair<ViewLayer, String> idsToSearch(List<ViewLayerRow<XyzFeature, XyzFeatureCodec>> multipleResults) {
      return null;
    }
  }

  static class CustomMergeOperation implements MergeOperation<XyzFeature, XyzFeatureCodec> {

    @Override
    public XyzFeatureCodec apply(List<ViewLayerRow<XyzFeature, XyzFeatureCodec>> sameFeatureFromEachStorage) {
      return sameFeatureFromEachStorage.stream()
          .min(Comparator.comparing(ViewLayerRow::getStoragePriority))
          .map(ViewLayerRow::getRow)
          .get();
    }
  }
}
