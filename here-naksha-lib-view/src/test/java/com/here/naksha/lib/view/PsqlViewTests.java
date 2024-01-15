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
import com.here.naksha.lib.core.models.geojson.implementation.XyzPoint;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.SOp;
import com.here.naksha.lib.core.models.storage.SeekableCursor;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzCollectionCodec;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.psql.PsqlFeatureGenerator;
import com.here.naksha.lib.psql.PsqlStorage;
import com.here.naksha.lib.psql.PsqlStorage.Params;
import com.here.naksha.lib.psql.PsqlStorageConfig;
import com.here.naksha.lib.psql.PsqlWriteSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.here.naksha.lib.core.models.storage.SOp.intersects;
import static com.here.naksha.lib.psql.PsqlStorageConfig.configFromFileOrEnv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Base class for all PostgresQL tests that require some test database.
 */
@SuppressWarnings("unused")
@TestMethodOrder(OrderAnnotation.class)
class PsqlViewTests {

  static final Logger log = LoggerFactory.getLogger(PsqlViewTests.class);

  /**
   * The test database, if any is available.
   */
  @SuppressWarnings("unused")
  static final @NotNull PsqlStorageConfig config =
      configFromFileOrEnv("test_psql_db.url", "NAKSHA_TEST_PSQL_DB_URL", "naksha_view_test_schema");

  /**
   * Prevents that the test drops the schema at the start.
   */
  static final boolean DROP_INITIALLY = true;

  /**
   * If the test drop the database at the end (false by default, to verify results).
   */
  static final boolean DROP_FINALLY = false;

  final boolean runTest() {
    return true;
  }

  final boolean dropInitially() {
    return runTest() && DROP_INITIALLY;
  }

  final boolean dropFinally() {
    return runTest() && DROP_FINALLY;
  }

  static final String TEST_APP_ID = "test_app";
  static final String TEST_AUTHOR = "test_author";
  static final String COLLECTION_0 = "test_view0";
  static final String COLLECTION_1 = "test_view1";
  static final String COLLECTION_2 = "test_view2";

  static @Nullable PsqlStorage storage;
  static @Nullable NakshaContext nakshaContext;
  static @Nullable PsqlWriteSession session;

  @BeforeAll
  static void beforeTest() {
    NakshaContext.currentContext().setAuthor("PsqlStorageTest");
    NakshaContext.currentContext().setAppId("naksha-lib-view-unit-tests");
    nakshaContext = new NakshaContext().withAppId(TEST_APP_ID).withAuthor(TEST_AUTHOR);
  }

  /**
   * The test-schema to use, can be overridden to switch the schema.
   */
  @NotNull
  String schema() {
    assertNotNull(schema);
    return schema;
  }

  private String schema;

  @Test
  @Order(10)
  @EnabledIf("runTest")
  void createStorage() {
    storage = new PsqlStorage(config);
    schema = storage.getSchema();
    if (!schema.equals(schema())) {
      storage.setSchema(schema());
    }
    // Enable this code line to get debug output from the database!
    // storage.setLogLevel(EPsqlLogLevel.DEBUG);
  }

  @Test
  @Order(11)
  @EnabledIf("dropInitially")
  void dropSchemaIfExists() {
    assertNotNull(storage);
    storage.dropSchema();
  }

  @Test
  @Order(13)
  @EnabledIf("runTest")
  void initStorage() {
    assertNotNull(storage);
    storage.initStorage(new Params().pg_hint_plan(false).pg_stat_statements(false));
  }

  @Test
  @Order(20)
  @EnabledIf("runTest")
  void startWriteSession() {
    assertNotNull(storage);
    session = storage.newWriteSession(nakshaContext, true);
    assertNotNull(session);
  }

  @Test
  @Order(30)
  @EnabledIf("runTest")
  void createCollection() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteXyzCollections request = new WriteXyzCollections();
    request.add(EWriteOp.CREATE, new XyzCollection(COLLECTION_0, false, false, true));
    request.add(EWriteOp.CREATE, new XyzCollection(COLLECTION_1, false, false, true));
    request.add(EWriteOp.CREATE, new XyzCollection(COLLECTION_2, false, false, true));
    try (final ForwardCursor<XyzCollection, XyzCollectionCodec> cursor =
             session.execute(request).getXyzCollectionCursor()) {
      assertNotNull(cursor);
      assertTrue(cursor.hasNext());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(31)
  @EnabledIf("runTest")
  void addFeatures() {
    assertNotNull(storage);
    assertNotNull(session);
    PsqlFeatureGenerator fg = new PsqlFeatureGenerator();
    final WriteXyzFeatures requestTest0 = new WriteXyzFeatures(COLLECTION_0);
    final WriteXyzFeatures requestTest1 = new WriteXyzFeatures(COLLECTION_1);
    final WriteXyzFeatures requestTest2 = new WriteXyzFeatures(COLLECTION_2);
    for (int i = 0; i < 10; i++) {
      final XyzFeature feature = fg.newRandomFeature();
      feature.setGeometry(new XyzPoint(0d, 0d));
      requestTest0.add(EWriteOp.PUT, feature);

      XyzFeature featureEdited1 = feature.deepClone();
      featureEdited1.setGeometry(new XyzPoint(1d, 1d));
      requestTest1.add(EWriteOp.PUT, featureEdited1);

      XyzFeature featureEdited2 = feature.deepClone();
      featureEdited2.setGeometry(new XyzPoint(2d, 2d));
      requestTest2.add(EWriteOp.PUT, featureEdited2);
    }

    try {
      session.execute(requestTest0);
      session.execute(requestTest1);
      session.execute(requestTest2);
    } finally {
      session.commit(true);
    }
  }


  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_pickTopLayerResult() throws NoCursor {
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
    List<XyzFeatureCodec> features = queryView(view, requestAll);
    List<XyzFeatureCodec> features1 = queryView(viewReversed, requestAll);

    // then
    assertEquals(10, features.size());
    assertEquals(0d, features.get(0).getGeometry().getCoordinate().x);

    assertEquals(10, features1.size());
    assertEquals(1d, features1.get(0).getGeometry().getCoordinate().x);
    session.commit(true);
  }

  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_fetchMissing() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1);
    View view = new View(viewLayerCollection);

    ReadFeatures getByPoint = new ReadFeatures();
    getByPoint.setSpatialOp(intersects(new XyzPoint(1d, 1d)));

    // when
    List<XyzFeatureCodec> features = queryView(view, getByPoint);

    // then
    assertEquals(10, features.size());
    // feature fetched in second query from obligatory storage
    assertEquals(0d, features.get(0).getGeometry().getCoordinate().x);

    session.commit(true);
  }

  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_missingMiddleLayerInSpacialQuery() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);
    ViewLayer layer2 = new ViewLayer(storage, COLLECTION_2);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1, layer2);
    View view = new View(viewLayerCollection);

    ReadFeatures getByPoint = new ReadFeatures();
    getByPoint.setSpatialOp(SOp.or(intersects(new XyzPoint(0d, 0d)), intersects(new XyzPoint(2d, 2d))));

    // when
    List<XyzFeatureCodec> features = queryView(view, getByPoint);

    // then
    assertEquals(10, features.size());
    assertEquals(0d, features.get(0).getGeometry().getCoordinate().x);

    session.commit(true);
  }

  @Test
  @Order(41)
  @EnabledIf("runTest")
  void viewQueryTest_returnFromMiddleLayerIfFeatureIsMissingInTopLayer() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    // given feature in COLLECTION 1 and 2 (but not in 0)
    PsqlFeatureGenerator fg = new PsqlFeatureGenerator();
    final WriteXyzFeatures requestTest1 = new WriteXyzFeatures(COLLECTION_1);
    final WriteXyzFeatures requestTest2 = new WriteXyzFeatures(COLLECTION_2);
    final XyzFeature feature = fg.newRandomFeature();
    feature.setGeometry(new XyzPoint(1d, 1d));
    requestTest1.add(EWriteOp.CREATE, feature);

    XyzFeature featureEdited2 = feature.deepClone();
    featureEdited2.setGeometry(new XyzPoint(2d, 2d));
    requestTest2.add(EWriteOp.CREATE, featureEdited2);
    try {
      session.execute(requestTest1);
      session.execute(requestTest2);
    } finally {
      session.commit(true);
    }

    // given view
    ViewLayer layer0 = new ViewLayer(storage, COLLECTION_0);
    ViewLayer layer1 = new ViewLayer(storage, COLLECTION_1);
    ViewLayer layer2 = new ViewLayer(storage, COLLECTION_2);

    ViewLayerCollection viewLayerCollection = new ViewLayerCollection("", layer0, layer1, layer2);
    View view = new View(viewLayerCollection);

    // when requesting for feature from COLLECTION 0, 1 and 2
    ReadFeatures requestById = new ReadFeatures();
    requestById.setPropertyOp(POp.eq(PRef.id(), feature.getId()));
    List<XyzFeatureCodec> features = queryView(view, requestById);

    // then should get result from COLLECTION_1 as it's next in priority and feature doesn't exist in COLLECTION_0 which is top priority layer.
    assertEquals(1, features.size());
    assertEquals(1d, features.get(0).getGeometry().getCoordinate().x);
    session.commit(true);
  }

  @Test
  @Order(9999)
  @EnabledIf("dropFinally")
  void dropSchemaFinally() {
    assertNotNull(storage);
    storage.dropSchema();
  }

  @EnabledIf("runTest")
  @AfterAll
  static void afterTest() {
    if (session != null) {
      try {
        session.close();
      } catch (Exception e) {
        log.atError()
            .setMessage("Failed to close write-session")
            .setCause(e)
            .log();
      } finally {
        session = null;
      }
    }
    if (storage != null) {
      try {
        storage.close();
      } catch (Exception e) {
        log.atError().setMessage("Failed to close storage").setCause(e).log();
      } finally {
        storage = null;
      }
    }
  }

  private List<XyzFeatureCodec> queryView(View view, ReadFeatures request) throws NoCursor {
    ViewReadSession readSession = view.newReadSession(nakshaContext, false);
    try (final SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
             readSession.execute(request).getXyzSeekableCursor()) {
      return cursor.asList();
    } finally {
      readSession.close();
    }
  }
}
