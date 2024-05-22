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
package com.here.naksha.lib.psql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.here.naksha.lib.base.Base;
import com.here.naksha.lib.base.DeleteFeature;
import com.here.naksha.lib.base.Geometry;
import com.here.naksha.lib.base.GeometryHelper;
import com.here.naksha.lib.base.IReadRowFilter;
import com.here.naksha.lib.base.InsertFeature;
import com.here.naksha.lib.base.JvmPObject;
import com.here.naksha.lib.base.NakCollection;
import com.here.naksha.lib.base.NakErrorResponse;
import com.here.naksha.lib.base.NakFeature;
import com.here.naksha.lib.base.NakLineString;
import com.here.naksha.lib.base.NakPoint;
import com.here.naksha.lib.base.NakProperties;
import com.here.naksha.lib.base.NakResponse;
import com.here.naksha.lib.base.NakSuccessResponse;
import com.here.naksha.lib.base.NakTags;
import com.here.naksha.lib.base.NakXyz;
import com.here.naksha.lib.base.PurgeFeature;
import com.here.naksha.lib.base.ReadRow;
import com.here.naksha.lib.base.UpdateFeature;
import com.here.naksha.lib.base.WriteCollections;
import com.here.naksha.lib.base.WriteFeature;
import com.here.naksha.lib.base.WriteFeatures;
import com.here.naksha.lib.base.WriteOp;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzGeometry;
import com.here.naksha.lib.core.models.geojson.implementation.XyzPoint;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.storage.EExecutedOp;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.MutableCursor;
import com.here.naksha.lib.core.models.storage.NonIndexedPRef;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.SOp;
import com.here.naksha.lib.core.models.storage.SeekableCursor;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.lib.jbon.BigInt64Kt;
import com.here.naksha.lib.jbon.JvmEnv;
import com.here.naksha.lib.jbon.NakshaTxn;
import com.here.naksha.lib.nak.Flags;
import com.here.naksha.lib.plv8.ReqHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.postgresql.util.PSQLException;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.core.models.storage.POp.and;
import static com.here.naksha.lib.core.models.storage.POp.eq;
import static com.here.naksha.lib.core.models.storage.POp.exists;
import static com.here.naksha.lib.core.models.storage.POp.not;
import static com.here.naksha.lib.core.models.storage.PRef.id;
import static com.here.naksha.lib.core.models.storage.transformation.BufferTransformation.bufferInMeters;
import static com.here.naksha.lib.core.models.storage.transformation.BufferTransformation.bufferInRadius;
import static com.here.naksha.lib.core.util.storage.RequestHelper.createBBoxEnvelope;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"unused"})
@TestMethodOrder(OrderAnnotation.class)
public class PsqlStorageTests extends PsqlCollectionTests {

  @Override
  boolean enabled() {
    return true;
  }

  final @NotNull String collectionId() {
    return "foo";
  }

  @Override
  boolean partition() {
    return true;
  }

  static final String SINGLE_FEATURE_ID = "TheFeature";
  static final String SINGLE_FEATURE_INITIAL_TAG = "@:foo:world";
  static final String SINGLE_FEATURE_REPLACEMENT_TAG = "@:foo:bar";
  static final Flags DEFAULT_FLAGS = new Flags();

  @Test
  @Order(50)
  @EnabledIf("runTest")
  void singleFeatureCreate() {
    assertNotNull(storage);
    assertNotNull(session);
    final NakFeature feature = new NakFeature();
    feature.setId(SINGLE_FEATURE_ID);
    Geometry geometry = new Geometry();
    geometry.setCoordinates(new NakPoint(5.0d, 6.0d, 2.0d));
    feature.setGeometry(geometry);
    NakXyz xyz = new NakXyz();
    xyz.setTags(new NakTags(SINGLE_FEATURE_INITIAL_TAG));
    NakProperties nakProperties = new NakProperties();
    nakProperties.setXyz(xyz);
    feature.setProperties(nakProperties);
    InsertFeature nakInsertFeature = new InsertFeature(collectionId(), DEFAULT_FLAGS, feature);
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), nakInsertFeature);
    try {
      final NakResponse response = session.execute(request);
      assertInstanceOf(NakSuccessResponse.class, response);
      final NakSuccessResponse successResp = (NakSuccessResponse) response;
      assertEquals(1, successResp.getRows().length);
      ReadRow first = Arrays.stream(successResp.getRows()).findFirst().get();
      assertSame(EExecutedOp.CREATED.toString(), first.getOp());
      final String id = first.getId();
      assertEquals(SINGLE_FEATURE_ID, id);
      assertNotNull(first.getRow().getUuid());
      NakFeature f = first.getFeature();
      NakPoint point = Base.assign(f.getGeometry().getCoordinates(), NakPoint.getKlass());
      assertNotNull(point);
      assertEquals(5.0d, point.getLongitude());
      assertEquals(6.0d, point.getLatitude());
      assertEquals(2.0d, point.getAltitude());
      assertEquals(SINGLE_FEATURE_ID, f.getId());
      assertEquals(1, f.getProperties().getXyz().getUid());
      assertSame(EXyzAction.CREATE.toString(), f.getProperties().getXyz().getAction());
      assertEquals(List.of(SINGLE_FEATURE_INITIAL_TAG), f.getProperties().getXyz().getTags());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(51)
  @EnabledIf("runTest")
  void verifyTransactionCounts() throws NoCursor {
    ReadFeatures readFeatures = new ReadFeatures("naksha~transactions");
    try (final ForwardCursor<String, StringCodec> cursor =
             session.execute(readFeatures).cursor(new StringCodecFactory())) {
      // there should be just one transaction log at the moment (single feature has been created)
      assertTrue(cursor.next());
      final String[] idFields = cursor.getId().split(":");
      assertEquals(storage.getStorageId(), idFields[0]);
      assertEquals("txn", idFields[1]);
      assertEquals(4, idFields[2].length()); // year (4- digits)
      assertTrue(idFields[3].length() <= 2); // month (1 or 2 digits)
      assertTrue(idFields[4].length() <= 2); // day (1 or 2 digits)
      assertEquals("3", idFields[5]); // seq txn (first for internal collections, second for feature create collection, third for create feature)
      assertEquals("0", idFields[6]); // uid seq

      Map<String, Object> featureAsMap = (Map<String, Object>) JvmEnv.get().parse(cursor.getFeature());
      assertEquals(1, featureAsMap.get("modifiedFeatureCount"));
      assertEquals(1, ((Map<String, Integer>) featureAsMap.get("collectionCounters")).get(collectionId()));
      assertNull(featureAsMap.get("seqNumber"));
    }
  }

  @Test
  @Order(51)
  @EnabledIf("runTest")
  void singleFeatureRead() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    ReadFeatures readFeatures = new ReadFeatures(collectionId());
    readFeatures.setPropertyOp(eq(id(), SINGLE_FEATURE_ID));
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertTrue(cursor.hasNext());
      assertTrue(cursor.next());
      final EExecutedOp op = cursor.getOp();
      assertSame(EExecutedOp.READ, op);

      final XyzFeature feature = cursor.getFeature();
      XyzNamespace xyz = feature.xyz();

      // then
      assertEquals(SINGLE_FEATURE_ID, feature.getId());
      assertEquals(1, xyz.getVersion());
      assertSame(EXyzAction.CREATE, xyz.getAction());
      final XyzGeometry geometry = feature.getGeometry();
      assertNotNull(geometry);
      final Coordinate coordinate = geometry.getJTSGeometry().getCoordinate();
      assertEquals(5.0d, coordinate.getOrdinate(0));
      assertEquals(6.0d, coordinate.getOrdinate(1));
      assertEquals(2.0d, coordinate.getOrdinate(2));

      final String uuid = cursor.getUuid();
      assertEquals(cursor.getUuid(), xyz.getUuid());
      final String[] uuidFields = uuid.split(":");
      assertEquals(storage.getStorageId(), uuidFields[0]);
      assertEquals(collectionId(), uuidFields[1]);
      assertEquals(4, uuidFields[2].length()); // year (4- digits)
      assertTrue(uuidFields[3].length() <= 2); // month (1 or 2 digits)
      assertTrue(uuidFields[4].length() <= 2); // day (1 or 2 digits)
      assertEquals("3", uuidFields[5]); // seq txn (first for internal collections, second for feature create collection, third for create feature)
      assertEquals("0", uuidFields[6]); // uid seq
      assertEquals(TEST_APP_ID, xyz.getAppId());
      assertEquals(TEST_AUTHOR, xyz.getAuthor());
      assertEquals(xyz.getCreatedAt(), xyz.getUpdatedAt());

      // FIXME after merge
//      assertEquals(encodeLatLon(coordinate.y, coordinate.x, 14), xyz.get("grid"));
      assertEquals(0, xyz.get("grid"));

      assertEquals(List.of(SINGLE_FEATURE_INITIAL_TAG), xyz.getTags());

      assertFalse(cursor.hasNext());
    }
  }

  @Test
  @Order(52)
  @EnabledIf("runTest")
  void readByBbox() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);


    org.locationtech.jts.geom.Geometry envelopeBbox = createBBoxEnvelope(4.0d, 5.0, 5.5d, 6.5);

    ReadFeatures readFeatures = new ReadFeatures(collectionId());
    BufferOp.bufferOp(envelopeBbox, 1.0);
    readFeatures.setSpatialOp(SOp.intersects(envelopeBbox));

    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertTrue(cursor.next());
      // then
      assertEquals(SINGLE_FEATURE_ID, cursor.getFeature().getId());
      assertFalse(cursor.hasNext());
    }
  }

  @Test
  @Order(52)
  @EnabledIf("runTest")
  void readWithBuffer() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    XyzPoint xyzPoint = new XyzPoint(4.0d, 5.0d);

    ReadFeatures readFeatures = new ReadFeatures(collectionId());
    readFeatures.setSpatialOp(SOp.intersects(xyzPoint, bufferInRadius(1.0)));

    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertFalse(cursor.hasNext());
    }

    readFeatures.setSpatialOp(SOp.intersects(xyzPoint, bufferInRadius(2.0)));
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertTrue(cursor.hasNext());
    }
  }

  @Test
  @Order(52)
  @EnabledIf("runTest")
  void readWithBufferInMeters() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    XyzPoint xyzPoint = new XyzPoint(4.0d, 5.0d);

    ReadFeatures readFeatures = new ReadFeatures(collectionId());
    readFeatures.setSpatialOp(SOp.intersects(xyzPoint, bufferInMeters(150000.0)));

    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertFalse(cursor.hasNext());
    }

    readFeatures.setSpatialOp(SOp.intersects(xyzPoint, bufferInMeters(160000.0)));
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertTrue(cursor.next());
      double distanceInRadius = xyzPoint.getJTSGeometry().distance(cursor.getGeometry());
      // this is very inaccurate method to calculate meters, but it's enough for test purpose
      double distanceInMeters = distanceInRadius * DistanceUtils.DEG_TO_KM * 1000;
      assertTrue(150000.0 < distanceInMeters && distanceInMeters < 160000.0, "Real: " + distanceInMeters);
    }
  }

  @Test
  @Order(54)
  @EnabledIf("runTest")
  void singleFeatureUpsert() {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    final NakFeature featureToUpdate = new NakFeature();
    featureToUpdate.setId(SINGLE_FEATURE_ID);
    final NakPoint xyzGeometry = new NakPoint(5.0d, 6.0d, 2.0d);
    featureToUpdate.setGeometry(new Geometry(xyzGeometry));
    featureToUpdate.getProperties().getXyz().setTags(new NakTags(SINGLE_FEATURE_INITIAL_TAG, SINGLE_FEATURE_REPLACEMENT_TAG));

    WriteFeatures writeFeatures = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), new WriteFeature(collectionId(), DEFAULT_FLAGS, featureToUpdate));
    // when
    try {
      NakResponse nakResponse = session.execute(writeFeatures);
      NakSuccessResponse successResponse = (NakSuccessResponse) nakResponse;
      // then
      ReadRow row1 = successResponse.getRows()[0];
      final NakFeature feature = row1.getFeature();
      assertSame(EExecutedOp.UPDATED.toString(), row1.getOp());
      assertEquals(SINGLE_FEATURE_ID, feature.getId());
      final Geometry geometry = feature.getGeometry();
      assertEquals(xyzGeometry, geometry.getCoordinates());
      assertEquals(
          asList(SINGLE_FEATURE_INITIAL_TAG, SINGLE_FEATURE_REPLACEMENT_TAG),
          feature.getProperties().getXyz().getTags());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(55)
  @EnabledIf("runTest")
  void singleFeatureUpdate() {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    /**
     * data inserted in {@link #singleFeatureCreate()} test
     */
    final NakFeature featureToUpdate = new NakFeature(SINGLE_FEATURE_ID);
    // different geometry
    NakPoint newPoint1 = new NakPoint(5.1d, 6.0d, 2.1d);
    NakPoint newPoint2 = new NakPoint(5.15d, 6.0d, 2.15d);
    NakLineString lineString = new NakLineString(newPoint1, newPoint2);
    Geometry geometry = new Geometry(lineString);

    featureToUpdate.setGeometry(geometry);
    // This tag should replace the previous one!
    NakXyz xyz = new NakXyz(featureToUpdate);
    xyz.setTags(new NakTags(SINGLE_FEATURE_REPLACEMENT_TAG));
    featureToUpdate.getProperties().setXyz(xyz);
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), new UpdateFeature(collectionId(), DEFAULT_FLAGS, featureToUpdate));
    // when
    try {
      NakResponse nakResponse = session.execute(request);
      NakSuccessResponse successResponse = (NakSuccessResponse) nakResponse;
      ReadRow row = successResponse.getRows()[0];

      // then
      final NakFeature feature = row.getFeature();
      assertSame(EExecutedOp.UPDATED, row.getOp());
      assertEquals(SINGLE_FEATURE_ID, row.getId());
      //      assertNotNull(cursor.getPropertiesType());
      final Geometry respGeometry = row.getFeature().getGeometry();
      assertEquals(lineString, respGeometry.getCoordinates());
      assertEquals(List.of(SINGLE_FEATURE_REPLACEMENT_TAG), feature.getProperties().getXyz().getTags());
    } finally {
      session.commit(true);
    }
  }

  private static final int GUID_STORAGE_ID = 0;
  private static final int GUID_COLLECTION_ID = 1;
  private static final int GUID_YEAR = 2;
  private static final int GUID_MONTH = 3;
  private static final int GUID_DAY = 4;
  private static final int GUID_SEQ = 5;
  private static final int GUID_ID = 6;

  @Test
  @Order(56)
  @EnabledIf("runTest")
  void singleFeatureUpdateVerify() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    /**
     * data inserted in {@link #singleFeatureCreate()} test and updated by {@link #singleFeatureUpdate()}.
     */
    final ReadFeatures request = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);

    // when
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzFeatureCursor()) {
      cursor.next();
      final XyzFeature feature = cursor.getFeature();
      XyzNamespace xyz = feature.xyz();

      // then
      assertEquals(SINGLE_FEATURE_ID, feature.getId());
      assertEquals(3, xyz.getVersion());
      assertSame(EXyzAction.UPDATE, xyz.getAction());
      final XyzGeometry geometry = feature.getGeometry();
      assertNotNull(geometry);
      Coordinate expectedGeometry = new Coordinate(5.15d, 6.0d, 2.15d);
      assertEquals(expectedGeometry, geometry.getJTSGeometry().getCoordinate());

      final String uuid = cursor.getUuid();
      assertEquals(cursor.getUuid(), xyz.getUuid());
      final String[] uuidFields = uuid.split(":");

      assertEquals(storage.getStorageId(), uuidFields[GUID_STORAGE_ID]);
      assertEquals(collectionId(), uuidFields[GUID_COLLECTION_ID]);
      assertEquals(4, uuidFields[GUID_YEAR].length()); // year (4- digits)
      assertTrue(uuidFields[GUID_MONTH].length() <= 2); // month (1 or 2 digits)
      assertTrue(uuidFields[GUID_DAY].length() <= 2); // day (1 or 2 digits)
      // Note: the txn seq should be 4 as:
      // 1 - used for create internal collections
      // 2 - used for create collection
      // 3 - used for insert feature
      // 4 - used for upsert
      // 5 - used for update
      assertEquals("5", uuidFields[GUID_SEQ]);
      // Note: for each new txn_seq we reset uid to 0
      assertEquals("0", uuidFields[GUID_ID]);
      // Note: We know that if the schema was dropped, the transaction number is reset to 0.
      // - Create the collection in parent PsqlTest (0) <- commit
      // - Create the single feature (1) <- commit
      // - Upsert the single feature (2) <- commit
      // - Update the single feature (3) <- commit
      if (dropInitially()) {
        NakshaTxn nakshaTxn = new NakshaTxn(BigInt64Kt.BigInt64(xyz.getTxn()));
        assertEquals(uuidFields[GUID_YEAR], "" + nakshaTxn.year());
        assertEquals(uuidFields[GUID_MONTH], "" + nakshaTxn.month());
        assertEquals(uuidFields[GUID_DAY], "" + nakshaTxn.day());
        assertEquals(uuidFields[GUID_SEQ], "" + nakshaTxn.seq());
      }
      assertEquals(TEST_APP_ID, xyz.getAppId());
      assertEquals(TEST_AUTHOR, xyz.getAuthor());

      Point centroid = geometry.getJTSGeometry().getCentroid();
      // FIXME after merge of grid heretile feature
//      assertEquals(encodeLatLon(centroid.getY(), centroid.getX(), 14), xyz.get("grid"));
      assertEquals(0, xyz.get("grid"));
      assertFalse(cursor.hasNext());
    }
  }

  @Test
  @Order(56)
  @EnabledIf("runTest")
  void singleFeatureGetAllVersions() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    /**
     * data inserted in {@link #singleFeatureCreate()} test and updated by {@link #singleFeatureUpdate()}.
     */
    final ReadFeatures request = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    request.withReturnAllVersions(true);

    // when
    try (final MutableCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzMutableCursor()) {
      cursor.next();
      XyzFeature ver1 = cursor.getFeature();
      cursor.next();
      XyzFeature ver2 = cursor.getFeature();
      cursor.next();
      XyzFeature ver3 = cursor.getFeature();
      assertFalse(cursor.hasNext());
      assertEquals(ver1.getId(), ver2.getId());
      assertEquals(ver1.getId(), ver3.getId());
      assertNotEquals(ver1.getProperties().getXyzNamespace().getTxn(), ver2.getProperties().getXyzNamespace().getTxn());
    }
  }

  @Test
  @Order(56)
  @EnabledIf("runTest")
  void singleFeatureGetSpecificVersionRead() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    /**
     * data inserted in {@link #singleFeatureCreate()} test and updated by {@link #singleFeatureUpdate()}.
     */
    final ReadFeatures requestForTxn = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    requestForTxn.withReturnAllVersions(true);
    Long txnOfMiddleVersion;
    // when
    try (final MutableCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(requestForTxn).getXyzMutableCursor()) {
      cursor.next();
      cursor.next();
      cursor.next();
      txnOfMiddleVersion = cursor.getFeature().getProperties().getXyzNamespace().getTxn();
    }

    final ReadFeatures request = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    request.withReturnAllVersions(true);
    request.setPropertyOp(POp.eq(PRef.txn(), txnOfMiddleVersion));

    String puuid;
    // when
    try (final MutableCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzMutableCursor()) {
      cursor.next();
      XyzNamespace xyzNamespace = cursor.getFeature().getProperties().getXyzNamespace();
      puuid = xyzNamespace.getPuuid();

      assertEquals(txnOfMiddleVersion, xyzNamespace.getTxn());
      assertFalse(cursor.hasNext());

    }

    // get previous version by uuid = puuid
    final ReadFeatures requestForPreviousVersion = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    requestForPreviousVersion.withReturnAllVersions(true);
    requestForPreviousVersion.setPropertyOp(POp.eq(PRef.uuid(), puuid));
    try (final MutableCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(requestForPreviousVersion).getXyzMutableCursor()) {
      cursor.next();
      XyzNamespace xyzNamespace = cursor.getFeature().getProperties().getXyzNamespace();
      assertEquals(puuid, xyzNamespace.getUuid());
      assertTrue(txnOfMiddleVersion > xyzNamespace.getTxn());
      assertFalse(cursor.hasNext());
    }
  }

  @Test
  @Order(57)
  @EnabledIf("runTest")
  void singleFeaturePutWithSameId() {
    assertNotNull(storage);
    assertNotNull(session);
    final NakFeature feature = new NakFeature((SINGLE_FEATURE_ID));
    final Geometry geometry = new Geometry();
    geometry.setCoordinates(new NakPoint(5.0d, 6.0d, 2.0d));
    feature.setGeometry(geometry);
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), new WriteFeature(collectionId(), DEFAULT_FLAGS, feature));
    try {
      NakSuccessResponse response = (NakSuccessResponse) session.execute(request);
      // should change to operation update as row already exists.
      assertSame(EExecutedOp.UPDATED.toString(), response.getRows()[0].getOp());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(60)
  @EnabledIf("runTest")
  void testDuplicateFeatureId() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    final NakFeature feature = new NakFeature(SINGLE_FEATURE_ID);
    feature.setGeometry(GeometryHelper.INSTANCE.pointGeometry(0.0d, 0.0d, 0.0d));
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), new InsertFeature(collectionId(), DEFAULT_FLAGS, feature));
    // when
    final NakResponse result = session.execute(request);

    // then
    assertInstanceOf(NakErrorResponse.class, result);
    NakErrorResponse errorResult = (NakErrorResponse) result;
    assertEquals(XyzError.CONFLICT.toString(), errorResult.getError());
    assertTrue(errorResult.getMessage().startsWith("ERROR: duplicate key value violates unique"));
    session.commit(true);

    // make sure feature hasn't been updated (has old geometry).
    final ReadFeatures readRequest = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readRequest).getXyzFeatureCursor()) {
      assertTrue(cursor.next());
      assertEquals(
          new Coordinate(5d, 6d, 2d),
          cursor.getFeature().getGeometry().getJTSGeometry().getCoordinate());
    }
  }

  @Test
  @Order(61)
  @EnabledIf("runTest")
  void testMultiOperationPartialFailCausesOverallFailure() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    final String UNIQUE_VIOLATION_MSG =
        format("The feature with the id '%s' does exist already", SINGLE_FEATURE_ID);

    // given
    final NakFeature featureToSucceed = new NakFeature("123");
    final NakFeature featureToFail = new NakFeature(SINGLE_FEATURE_ID);
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(
        collectionId(),
        new InsertFeature(collectionId(), DEFAULT_FLAGS, featureToSucceed),
        new InsertFeature(collectionId(), DEFAULT_FLAGS, featureToFail)
    );

    // when
    final NakResponse result = session.execute(request);

    // then
    assertInstanceOf(NakErrorResponse.class, result);
    NakErrorResponse errorResult = (NakErrorResponse) result;
    assertEquals(XyzError.CONFLICT.toString(), errorResult.getError());
    assertTrue(errorResult.getMessage().startsWith("ERROR: duplicate key value violates unique"));

    // we don't have detailed information, we can work only in mode: all or nothing.
    session.commit(true);
    // verify if other feature has not been stored
    ReadFeatures readFeature = RequestHelper.readFeaturesByIdRequest(collectionId(), featureToSucceed.getId());
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeature).getXyzFeatureCursor()) {
      assertFalse(cursor.hasNext());
    }
  }

  @Test
  @Order(62)
  @EnabledIf("runTest")
  void testInvalidUuid() {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    DeleteFeature deleteOp = new DeleteFeature(collectionId(), SINGLE_FEATURE_ID, "invalid_UUID");
    WriteFeatures deleteReq = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), deleteOp);
    // when
    try {
      NakResponse result = session.execute(deleteReq);
      // then
      assertInstanceOf(NakErrorResponse.class, result);
      NakErrorResponse errorResult = (NakErrorResponse) result;
      assertEquals("NX000", errorResult.getError());
      assertTrue(errorResult.getMessage().contains("invalid naksha uuid invalid_UUID"));
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(64)
  @EnabledIf("runTest")
  void singleFeatureDeleteById() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    final NakFeature feature = new NakFeature("TO_DEL_BY_ID");
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), new InsertFeature(collectionId(), DEFAULT_FLAGS, feature));

    // when
    session.execute(request);
    session.commit(true);

    DeleteFeature deleteOp = new DeleteFeature(collectionId(), "TO_DEL_BY_ID", null);
    WriteFeatures deleteReq = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), deleteOp);
    try {
      NakSuccessResponse nakResponse = (NakSuccessResponse) session.execute(deleteReq);
      ReadRow row = nakResponse.getRows()[0];
      assertSame(EExecutedOp.DELETED.toString(), row.getOp());
      assertEquals("TO_DEL_BY_ID", row.getId());
      NakXyz xyzNamespace = row.getFeature().getProperties().getXyz();
      assertNotEquals(xyzNamespace.getCreatedAt(), xyzNamespace.getUpdatedAt());
      assertEquals(EXyzAction.DELETE.toString(), xyzNamespace.getAction());
      assertEquals(2, xyzNamespace.getVersion());
      assertEquals(1, nakResponse.getRows().length);
    } finally {
      session.commit(true);
    }

    // verify if hst contains 2 versions
    ReadFeatures read = RequestHelper.readFeaturesByIdRequest(collectionId(), "TO_DEL_BY_ID");
    read.withReturnAllVersions(true);
    try (final MutableCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(read).mutableCursor()) {
      assertTrue(cursor.next());
      assertEquals(EXyzAction.CREATE, cursor.getFeature().getProperties().getXyzNamespace().getAction());
      assertTrue(cursor.next());
      assertEquals(EXyzAction.DELETE, cursor.getFeature().getProperties().getXyzNamespace().getAction());
    }
  }

  @Test
  @Order(65)
  @EnabledIf("runTest")
  void singleFeatureDeleteVerify() throws SQLException, NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    // when
    /**
     * Read from feature should return nothing.
     */
    final ReadFeatures request = RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzFeatureCursor()) {
      assertFalse(cursor.hasNext());
    }
    // also: direct query to feature table should return nothing.
    try (final PsqlReadSession session = storage.newReadSession(null, true)) {
      ResultSet rs = getFeatureFromTable(session, collectionId(), SINGLE_FEATURE_ID);
      assertFalse(rs.next());
    }

    /**
     * Read from deleted should return valid feature.
     */
    final ReadFeatures requestWithDeleted =
        RequestHelper.readFeaturesByIdRequest(collectionId(), SINGLE_FEATURE_ID);
    requestWithDeleted.withReturnDeleted(true);
    String featureJsonBeforeDeletion;

    /* TODO uncomment it when read with deleted is ready.

    try (final ResultCursor<XyzFeature> cursor =
    session.execute(requestWithDeleted).cursor()) {
    cursor.next();
    final XyzFeature feature = cursor.getFeature();
    XyzNamespace xyz = feature.xyz();

    // then
    assertSame(EExecutedOp.DELETED, cursor.getOp());
    final String id = cursor.getId();
    assertEquals(SINGLE_FEATURE_ID, id);
    final String uuid = cursor.getUuid();
    assertNotNull(uuid);
    final Geometry geometry = cursor.getGeometry();
    assertNotNull(geometry);
    assertEquals(new Coordinate(5.1d, 6.0d, 2.1d), geometry.getCoordinate());
    assertNotNull(feature);
    assertEquals(SINGLE_FEATURE_ID, feature.getId());
    assertEquals(uuid, feature.xyz().getUuid());
    assertSame(EXyzAction.DELETE, feature.xyz().getAction());
    featureJsonBeforeDeletion = cursor.getJson()
    assertFalse(cursor.next());
    }
    */
    /**
     * Check directly $del table.
     */
    final String collectionDelTableName = collectionId() + "$del";
    try (final PsqlReadSession session = storage.newReadSession(null, true)) {
      ResultSet rs = getFeatureFromTable(session, collectionDelTableName, SINGLE_FEATURE_ID);

      // feature exists in $del table
      assertTrue(rs.next());

      /* FIXME uncomment this when read with deleted is ready.
      assertEquals(featureJsonBeforeDeletion, rs.getString(1));
      */
    }
  }

  @Test
  @Order(66)
  @EnabledIf("runTest")
  void singleFeaturePurge() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    /**
     * Data inserted in {@link #singleFeatureCreate()} and deleted in {@link #singleFeatureDelete()}.
     * We don't care about geometry or other properties during PURGE operation, feature_id is only required thing,
     * thanks to that you don't have to read feature before purge operation.
     */
    PurgeFeature purgeOp = new PurgeFeature(collectionId(), SINGLE_FEATURE_ID, null);
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), purgeOp);

    // when
    try {
      NakSuccessResponse response = (NakSuccessResponse) session.execute(request);
      ReadRow row = response.getRows()[0];

      // then
      final NakFeature feature = row.getFeature();
      assertSame(EExecutedOp.PURGED, row.getOp());
      assertEquals(SINGLE_FEATURE_ID, row.getId());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(67)
  @EnabledIf("runTest")
  void singleFeaturePurgeVerify() throws SQLException {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    final String collectionDelTableName = collectionId() + "$del";

    // when
    try (final PsqlReadSession session = storage.newReadSession(null, true)) {
      ResultSet rs = getFeatureFromTable(session, collectionDelTableName, SINGLE_FEATURE_ID);

      // then
      assertFalse(rs.next());
    }
  }

  @Test
  @Order(67)
  @EnabledIf("runTest")
  void autoPurgeCheck() throws SQLException, NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    // given
    String collectionWithAutoPurge = collectionId() + "_ap";
    NakCollection collection = new NakCollection(collectionWithAutoPurge, partitionCount(), null, null, true, false);
    WriteCollections request = ReqHelper.INSTANCE.prepareCreateCollectionReq(collectionWithAutoPurge, null, collection, DEFAULT_FLAGS);

    // when
    try {
      NakResponse nakResponse = session.execute(request);
      assertInstanceOf(NakSuccessResponse.class, nakResponse);
      NakSuccessResponse successResponse = (NakSuccessResponse) nakResponse;
      NakCollection respCol = Base.assign(successResponse.getRows()[0].getFeature(), NakCollection.getKlass());
      assertTrue(respCol.isAutoPurge());
    } finally {
      session.commit(true);
    }

    // CREATE feature
    final NakFeature featureToDel = new NakFeature(SINGLE_FEATURE_ID);
    WriteFeatures requestFeature = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionWithAutoPurge, new InsertFeature(collectionWithAutoPurge, DEFAULT_FLAGS, featureToDel));
    try {
      NakResponse nakResponse = session.execute(requestFeature);
      assertInstanceOf(NakSuccessResponse.class, nakResponse);
    } finally {
      session.commit(true);
    }

    // DELETE feature
    DeleteFeature deleteOp = new DeleteFeature(collectionId(), "TO_DEL_BY_ID", null);
    WriteFeatures deleteReq = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), deleteOp);
    try {
      NakResponse nakResponse = session.execute(deleteReq);
      assertInstanceOf(NakSuccessResponse.class, nakResponse);
    } finally {
      session.commit(true);
    }

    // THEN should not exist in $del table (because auto-purge is ON)
    try (final PsqlReadSession session = storage.newReadSession(nakshaContext, true)) {
      ResultSet rs = getFeatureFromTable(session, collectionWithAutoPurge + "$del", SINGLE_FEATURE_ID);
      // then
      assertFalse(rs.next());
    }

    // but it should exist in $hst table
    try (final PsqlReadSession session = storage.newReadSession(nakshaContext, true)) {
      ResultSet rs = getFeatureFromTable(session, collectionWithAutoPurge + "$hst", SINGLE_FEATURE_ID);
      // then
      assertTrue(rs.next());
    }
  }

  @Test
  @Order(70)
  @EnabledIf("runTest")
  void multipleFeaturesInsert() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteXyzFeatures request = new WriteXyzFeatures(collectionId());
    int i = 0;
    boolean firstNameAdded = false;
    int size = 1000;
    WriteOp[] features = new WriteOp[size];
    while (i < size || !firstNameAdded) {
      final NakFeature feature = fg.newRandomFeature();
      if (!firstNameAdded) {
        firstNameAdded =
            Objects.equals(fg.firstNames[0], ((JvmPObject) feature.getProperties().data()).get("firstName"));
      }
      features[i] = new WriteFeature(collectionId(), DEFAULT_FLAGS, feature);
      i++;
    }
    WriteFeatures reqWrite = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), features);

    try {
      NakSuccessResponse nakResponse = (NakSuccessResponse) session.execute(reqWrite);
      for (ReadRow row : nakResponse.getRows()) {
        final String op = row.getOp();
        assertSame(EExecutedOp.CREATED.toString(), op);
        final String id = row.getId();
        assertNotNull(id);
        final String uuid = row.getUuid();
        assertNotNull(uuid);
        final Geometry geometry = row.getFeature().getGeometry();
        assertNotNull(geometry);
        final NakFeature f = row.getFeature();
        assertNotNull(f);
        assertEquals(id, f.getId());
        assertSame(EXyzAction.CREATE.toString(), f.getProperties().getXyz().getAction());
      }
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(71)
  @EnabledIf("runTest")
  void multipleFeaturesRead() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    final ReadFeatures request = new ReadFeatures(collectionId());
    request.setPropertyOp(POp.or(
        exists(PRef.tag("@:firstName:" + fg.firstNames[0])),
        exists(PRef.tag("@:firstName:" + fg.firstNames[1]))));
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzFeatureCursor()) {
      // We expect that at least one feature was found!
      assertTrue(cursor.hasNext());
      while (cursor.hasNext()) {
        assertTrue(cursor.next());
        final EExecutedOp op = cursor.getOp();
        assertSame(EExecutedOp.READ, op);
        final String id = cursor.getId();
        assertNotNull(id);
        final String uuid = cursor.getUuid();
        assertNotNull(uuid);
        final org.locationtech.jts.geom.Geometry geometry = cursor.getGeometry();
        assertNotNull(geometry);
        final XyzFeature f = cursor.getFeature();
        assertNotNull(f);
        assertEquals(id, f.getId());
        assertEquals(uuid, f.xyz().getUuid());
        assertSame(EXyzAction.CREATE, f.xyz().getAction());
        final List<@NotNull String> tags = f.xyz().getTags();
        assertNotNull(tags);
        assertTrue(tags.size() > 0);
        assertTrue(tags.contains("@:firstName:" + fg.firstNames[0])
                       || tags.contains("@:firstName:" + fg.firstNames[1]));
      }
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(72)
  @EnabledIf("runTest")
  void seekableCursorRead() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    final ReadFeatures request = new ReadFeatures(collectionId()).withLimit(null);
    try (final SeekableCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzSeekableCursor()) {

      // commit closes original cursor, but as we have all rows cached SeekableCursor should work as normal.
      session.commit(true);

      // We expect that at least one feature was found!
      assertTrue(cursor.hasNext());
      cursor.next();
      XyzFeature firstFeature = cursor.getFeature();
      while (cursor.hasNext()) {
        assertTrue(cursor.next());
        final XyzFeature f = cursor.getFeature();
        assertNotNull(f);
      }
      assertFalse(cursor.hasNext());

      cursor.beforeFirst();
      assertTrue(cursor.next());
      assertEquals(firstFeature, cursor.getFeature());
    }
  }

  @Test
  @Order(73)
  @EnabledIf("runTest")
  void testRestoreOrder() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    final NakFeature feature1 = new NakFeature("123");
    final NakFeature feature2 = new NakFeature("121");
    WriteFeatures request = new WriteFeatures(collectionId(), new InsertFeature[]{
        new InsertFeature(collectionId(), DEFAULT_FLAGS, feature1),
        new InsertFeature(collectionId(), DEFAULT_FLAGS, feature2)
    }, false, false, false, false, false, true, new IReadRowFilter[]{}
    );

    try {
      // when
      final NakSuccessResponse result = (NakSuccessResponse) session.execute(request);

      // then
      ReadRow[] rows = result.getRows();
      assertEquals("123", rows[0].getId());
      assertEquals("121", rows[1].getId());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(74)
  @EnabledIf("runTest")
  void limitedRead() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    limitToN(1L);
    limitToN(2L);
  }

  private void limitToN(final long limit) throws NoCursor {
    final ReadFeatures request = new ReadFeatures(collectionId()).withLimit(limit);
    try (final @NotNull ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(request).getXyzFeatureCursor()) {

      for (long row = 1; row <= limit; row++) {
        assertTrue(cursor.hasNext());
        assertTrue(cursor.next());
        assertNotNull(cursor.getFeature());
      }
      assertFalse(cursor.hasNext());
      assertThrowsExactly(NoSuchElementException.class, cursor::next);
    }
  }

  @Test
  @Order(110)
  @EnabledIf("runTest")
  void listAllCollections() throws SQLException {
    assertNotNull(storage);
    assertNotNull(session);
    //    ReadCollections readCollections =
    //        new ReadCollections().withReadDeleted(true).withIds(COLLECTION_ID);
    //    XyzFeatureReadResult<StorageCollection> readResult =
    //        (XyzFeatureReadResult<StorageCollection>) session.execute(readCollections);
    //    assertTrue(readResult.hasNext());
    //    final StorageCollection collection = readResult.next();
    //    assertNotNull(collection);
    //    assertEquals(COLLECTION_ID, collection.getId());
    //    //    assertTrue(collection.getHistory());
    //    assertEquals(Long.MAX_VALUE, collection.getMaxAge());
    //    assertEquals(0L, collection.getDeletedAt());
    //    assertFalse(readResult.hasNext());
  }

  @Test
  @Order(111)
  @EnabledIf("runTest")
  void intersectionSearch() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    final NakFeature feature = new NakFeature("otherFeature");
    Geometry geometry = GeometryHelper.INSTANCE.lineStringGeometry(
        new NakPoint(4.0d, 5.0),
        new NakPoint(4.0d, 6.0)
    );
    feature.setGeometry(geometry);
    WriteFeatures request = ReqHelper.INSTANCE.prepareFeatureReqForOperations(collectionId(), new InsertFeature(collectionId(), DEFAULT_FLAGS, feature));
    try {
      NakResponse nakResponse = session.execute(request);
      assertInstanceOf(NakSuccessResponse.class, nakResponse);
    } finally {
      session.commit(true);
    }

    // read by bbox that surrounds only first point

    org.locationtech.jts.geom.Geometry envelopeBbox = createBBoxEnvelope(3.9d, 4.9, 4.1d, 5.1);
    ReadFeatures readFeatures = new ReadFeatures(collectionId());
    readFeatures.setSpatialOp(SOp.intersects(envelopeBbox));

    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
             session.execute(readFeatures).getXyzFeatureCursor()) {
      assertTrue(cursor.next());
      // then
      assertEquals("otherFeature", cursor.getFeature().getId());
      assertFalse(cursor.hasNext());
    }
  }

  @Test
  @Order(112)
  @EnabledIf("runTest")
  void notIndexedPropertyRead() throws NoCursor, IOException {
    assertNotNull(storage);
    assertNotNull(session);

    com.here.naksha.lib.core.models.storage.WriteFeatures<String, StringCodec, ?> request = new com.here.naksha.lib.core.models.storage.WriteFeatures<>(new StringCodecFactory(), collectionId());

    // given
    final String jsonReference = "{\"id\":\"32167\",\"properties\":{\"weight\":60,\"length\":null,\"color\":\"red\",\"ids\":[0,1,9],\"ids2\":[\"a\",\"b\",\"c\"],\"subJson\":{\"b\":1},\"references\":[{\"id\":\"urn:here::here:Topology:106003684\",\"type\":\"Topology\",\"prop\":{\"a\":1}}]}}";
    ObjectReader reader = Json.get().reader();
    request.add(EWriteOp.CREATE, jsonReference);

    // FIXME TODO after having json to platform conversion
    fail();
//    try (final MutableCursor<String, StringCodec> cursor =
//             session.execute(request).mutableCursor(new StringCodecFactory())) {
//      assertTrue(cursor.next());
//    } finally {
//      session.commit(true);
//    }

    Consumer<ReadFeatures> expect = readFeaturesReq -> {
      try (final MutableCursor<String, StringCodec> cursor =
               session.execute(readFeaturesReq).mutableCursor(new StringCodecFactory())) {
        cursor.next();
        assertEquals("32167", cursor.getId());
        assertFalse(cursor.hasNext());
      } catch (NoCursor e) {
        throw unchecked(e);
      }
    };

    // when - search for int value
    ReadFeatures readFeatures = new ReadFeatures(collectionId());
    POp weightSearch = eq(new NonIndexedPRef("properties", "weight"), 60);
    readFeatures.setPropertyOp(weightSearch);
    // then
    expect.accept(readFeatures);

    // when - search 'not'
    readFeatures.setPropertyOp(not(eq(new NonIndexedPRef("properties", "weight"), 59)));
    // then
    expect.accept(readFeatures);

    // when - search 'exists'
    readFeatures.setPropertyOp(exists(new NonIndexedPRef("properties", "weight")));
    // then
    expect.accept(readFeatures);

    // when - search 'not exists'
    readFeatures.setPropertyOp(and(not(exists(new NonIndexedPRef("properties", "weight2"))), eq(id(), "32167")));
    // then
    expect.accept(readFeatures);

    // when - search not null value
    POp exSearch = POp.isNotNull(new NonIndexedPRef("properties", "color"));
    readFeatures.setPropertyOp(exSearch);
    // then
    expect.accept(readFeatures);

    // when - search null value
    POp nullSearch = POp.isNull(new NonIndexedPRef("properties", "length"));
    readFeatures.setPropertyOp(nullSearch);
    // then
    expect.accept(readFeatures);

    // when - search array contains
    POp arraySearch = POp.contains(new NonIndexedPRef("properties", "ids"), 9);
    readFeatures.setPropertyOp(arraySearch);
    // then
    expect.accept(readFeatures);

    // when - search array contains string
    POp arrayStringSearch = POp.contains(new NonIndexedPRef("properties", "ids2"), "a");
    readFeatures.setPropertyOp(arrayStringSearch);
    // then
    expect.accept(readFeatures);

    // when - search by json object
    POp jsonSearch2 = POp.contains(new NonIndexedPRef("properties", "references"),
        reader.readValue("[{\"id\":\"urn:here::here:Topology:106003684\"}]", ArrayList.class));
    readFeatures.setPropertyOp(jsonSearch2);
    // then
    expect.accept(readFeatures);

    // when - search by json object
    POp jsonSearch3 = POp.contains(new NonIndexedPRef("properties", "references"),
        reader.readValue("[{\"prop\":{\"a\":1}}]", JsonNode.class));
    readFeatures.setPropertyOp(jsonSearch3);
    // then
    expect.accept(readFeatures);

    // when - search by json object
    POp jsonSearch4 = POp.contains(new NonIndexedPRef("properties", "subJson"), reader.readValue("{\"b\":1}", JsonNode.class));
    readFeatures.setPropertyOp(jsonSearch3);
    // then
    expect.accept(readFeatures);
  }


  @Test
  @Order(120)
  @EnabledIf("runTest")
  void dropFooCollection() {
    assertNotNull(storage);
    assertNotNull(session);

    WriteCollections deleteCollection = ReqHelper.INSTANCE.deleteCollection(collectionId(), null);
    NakResponse nakResponse = session.execute(deleteCollection);
    assertInstanceOf(NakSuccessResponse.class, nakResponse);
    session.commit(true);

    // try readSession after purge, table doesn't exist anymore, so it should throw an exception.
    PsqlReadSession readDeletedSession = storage.newReadSession(nakshaContext, false);
    assertThrowsExactly(
        PSQLException.class,
        () -> getFeatureFromTable(readDeletedSession, collectionId(), SINGLE_FEATURE_ID),
        "ERROR: relation \"foo\" does not exist");
    readDeletedSession.close();
  }

  private ResultSet getFeatureFromTable(PsqlReadSession session, String table, String featureId) throws SQLException {
    final PostgresSession pgSession = session.session();
    final SQL sql = pgSession.sql().add("SELECT * from ").addIdent(table).add(" WHERE id = ? ;");
    final PreparedStatement stmt = pgSession.prepareStatement(sql);
    stmt.setString(1, featureId);
    return stmt.executeQuery();
  }
}
