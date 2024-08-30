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

import naksha.geo.PointCoord;
import naksha.geo.SpPoint;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.model.request.query.SpIntersects;
import naksha.model.request.query.SpOr;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for all PostgresQL tests that require some test database.
 */
@SuppressWarnings("unused")
@TestMethodOrder(OrderAnnotation.class)
class PsqlViewTests extends PsqlTests {

  static final Logger log = LoggerFactory.getLogger(PsqlViewTests.class);

  final boolean enabled() {
    return true;
  }

  final boolean dropInitially() {
    return runTest() && DROP_INITIALLY;
  }

  final boolean dropFinally() {
    return runTest() && DROP_FINALLY;
  }

  static final String COLLECTION_0 = "test_view0";
  static final String COLLECTION_1 = "test_view1";
  static final String COLLECTION_2 = "test_view2";

  @Test
  @Order(30)
  @EnabledIf("runTest")
  void createCollection() {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteRequest request = new WriteRequest();
    request.add(new Write().createCollection(null, new NakshaCollection(COLLECTION_0, 1, null, false, true, null)));
    request.add(new Write().createCollection(null, new NakshaCollection(COLLECTION_1, 1, null, false, true, null)));
    request.add(new Write().createCollection(null, new NakshaCollection(COLLECTION_2, 1, null, false, true, null)));
    SuccessResponse response = (SuccessResponse) session.execute(request);
    assertNotNull(response.getTuples());
    session.commit();
  }

  @Test
  @Order(31)
  @EnabledIf("runTest")
  void addFeatures() {
    assertNotNull(storage);
    assertNotNull(session);
    ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
    final WriteRequest requestTest0 = new WriteRequest();
    final WriteRequest requestTest1 = new WriteRequest();
    final WriteRequest requestTest2 = new WriteRequest();
    final SpPoint point = new SpPoint(new PointCoord(0d, 0d));
    final SpPoint point1 = new SpPoint(new PointCoord(1d, 1d));
    final SpPoint point2 = new SpPoint(new PointCoord(2d, 2d));
    for (int i = 0; i < 10; i++) {
      final NakshaFeature feature = new NakshaFeature(String.valueOf(threadLocalRandom.nextInt()));
      feature.setGeometry(point);
      requestTest0.add(new Write().updateFeature(null, COLLECTION_0, feature, false));

      NakshaFeature featureEdited1 = feature.copy(true);
      featureEdited1.setGeometry(point1);
      requestTest1.add(new Write().updateFeature(null, COLLECTION_1, featureEdited1, false));

      NakshaFeature featureEdited2 = feature.copy(true);
      featureEdited2.setGeometry(point2);
      requestTest2.add(new Write().updateFeature(null, COLLECTION_2, featureEdited2, false));
    }
      session.execute(requestTest0);
      session.execute(requestTest1);
      session.execute(requestTest2);
      session.commit();
  }


  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_pickTopLayerResult() {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1);
    View view = new View(viewLayerCollection);

    ViewLayerCollection viewLayerCollectionReversedOrder = new ViewLayerCollection("", layer1, layer0);
    View viewReversed = new View(viewLayerCollectionReversedOrder);

    ReadFeatures requestAll = new ReadFeatures();

    // when
    List<NakshaFeature> features = queryView(view, requestAll);
    List<NakshaFeature> features1 = queryView(viewReversed, requestAll);

    // then
    assertEquals(10, features.size());
    PointCoord coordinates = (PointCoord) features.get(0).getGeometry().getCoordinates();
    assertEquals(0d, coordinates.getLongitude());

    assertEquals(10, features1.size());
    PointCoord coordinates1 = (PointCoord) features1.get(0).getGeometry().getCoordinates();
    assertEquals(1d, coordinates1.getLongitude());
    session.commit();
  }

  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_fetchMissing() {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1);
    View view = new View(viewLayerCollection);

    ReadFeatures getByPoint = new ReadFeatures();
    RequestQuery requestQuery = new RequestQuery();
    SpIntersects spIntersects = new SpIntersects();
    spIntersects.setGeometry(new SpPoint(new PointCoord(1d,1d)));
    requestQuery.setSpatial(spIntersects);
    getByPoint.setQuery(requestQuery);

    // when
    List<NakshaFeature> features = queryView(view, getByPoint);

    // then
    assertEquals(10, features.size());
    // feature fetched in second query from obligatory storage
    SpPoint geometry = (SpPoint) features.get(0).getGeometry();
    assertEquals(0d, geometry.getCoordinates().getLongitude());

    session.commit();
  }

  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_missingMiddleLayerInSpacialQuery() {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);
    ViewLayer layer2 = new ViewLayer(storage, COLLECTION_2);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1, layer2);
    View view = new View(viewLayerCollection);

    ReadFeatures getByPoint = new ReadFeatures();
    RequestQuery requestQuery = new RequestQuery();
    SpIntersects spIntersects = new SpIntersects();
    spIntersects.setGeometry(new SpPoint(new PointCoord(0d,0d)));
    SpOr spOr = new SpOr();
    spOr.add(spIntersects);
    spIntersects.setGeometry(new SpPoint(new PointCoord(2d,2d)));
    spOr.add(spIntersects);
    requestQuery.setSpatial(spOr);
    getByPoint.setQuery(requestQuery);

    // when
    List<NakshaFeature> features = queryView(view, getByPoint);

    // then
    assertEquals(10, features.size());
    SpPoint geometry = (SpPoint) features.get(0).getGeometry();
    assertEquals(0d, geometry.getCoordinates().getLongitude());
    session.commit();
  }

  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_returnFromMiddleLayerIfFeatureIsMissingInTopLayer() {
    assertNotNull(storage);
    assertNotNull(session);
    ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();

    // given feature in COLLECTION 1 and 2 (but not in 0)
    final WriteRequest requestTest1 = new WriteRequest();
    final WriteRequest requestTest2 = new WriteRequest();
    final NakshaFeature feature = new NakshaFeature(String.valueOf(threadLocalRandom.nextInt()));
    feature.setGeometry(new SpPoint(new PointCoord(11d, 11d)));
    requestTest1.add(new Write().createFeature(null, COLLECTION_1, feature));

    NakshaFeature featureEdited2 = feature.copy(true);
    featureEdited2.setGeometry(new SpPoint(new PointCoord(22d, 22d)));
    requestTest2.add(new Write().createFeature(null, COLLECTION_2, featureEdited2));
      session.execute(requestTest1);
      session.execute(requestTest2);
      session.commit();


    // given view
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);
    ViewLayer layer2 = new ViewLayer(storage, COLLECTION_2);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1, layer2);
    View view = new View(viewLayerCollection);

    // when requesting for feature from COLLECTION 0, 1 and 2
    ReadFeatures getByPoint = new ReadFeatures();
    RequestQuery requestQuery = new RequestQuery();
    SpIntersects spIntersects = new SpIntersects();
    spIntersects.setGeometry(new SpPoint(new PointCoord(11d,11d)));
    SpOr spOr = new SpOr();
    spOr.add(spIntersects);
    spIntersects.setGeometry(new SpPoint(new PointCoord(22d,22d)));
    spOr.add(spIntersects);
    requestQuery.setSpatial(spOr);
    getByPoint.setQuery(requestQuery);
    List<NakshaFeature> features = queryView(view, getByPoint);

    // then should get result from COLLECTION_1 as it's next in priority and feature doesn't exist in COLLECTION_0 which is top priority layer.
    assertEquals(1, features.size());
    SpPoint geometry = (SpPoint) features.get(0).getGeometry();
    assertEquals(11d, geometry.getCoordinates().getLongitude());
    session.commit();
  }

  private List<NakshaFeature> queryView(View view, ReadFeatures request) {
    Response response = view.newReadSession(null).execute(request);
    assertInstanceOf(SuccessResponse.class,response);
    SuccessResponse successResponse = (SuccessResponse) response;
    return successResponse.getFeatures();
  }
}
