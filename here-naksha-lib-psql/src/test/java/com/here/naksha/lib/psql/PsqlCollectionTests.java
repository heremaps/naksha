package com.here.naksha.lib.psql;

import com.here.naksha.lib.base.Base;
import com.here.naksha.lib.base.NakCollection;
import com.here.naksha.lib.base.NakFeature;
import com.here.naksha.lib.base.NakReadRow;
import com.here.naksha.lib.base.NakResponse;
import com.here.naksha.lib.base.NakSuccessResponse;
import com.here.naksha.lib.base.NakWriteCollections;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.models.storage.EExecutedOp;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.XyzCollectionCodec;
import com.here.naksha.lib.jbon.JvmBigInt64Api;
import com.here.naksha.lib.plv8.ConstantsKt;
import com.here.naksha.lib.plv8.NakshaSession;
import com.here.naksha.lib.plv8.ReqHelper;
import com.here.naksha.lib.plv8.Static;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static com.here.naksha.lib.core.util.storage.RequestHelper.createFeatureRequest;
import static com.here.naksha.lib.core.util.storage.RequestHelper.updateFeatureRequest;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
abstract class PsqlCollectionTests extends PsqlTests {

  @Override
  protected PsqlStorage.Params getParams() {
    return super.getParams().pg_plv8(true);
  }

  @Test
  @Order(30)
  @EnabledIf("runTest")
  void createCollection() {
    assertNotNull(storage);
    assertNotNull(session);
    NakCollection nakCollection = new NakCollection(collectionId(), partitionCount(), null, null, false, false);
    NakWriteCollections collectionWriteReq = ReqHelper.INSTANCE.prepareCollectionReqCreateFromFeature(collectionId(), nakCollection);
    try {
      NakResponse response = session.execute(collectionWriteReq);
      assertInstanceOf(NakSuccessResponse.class, response);
      NakSuccessResponse successResponse = (NakSuccessResponse) response;
      NakReadRow responseRow = successResponse.getRows()[0];
      assertEquals(collectionId(), responseRow.getId());
      assertNotNull(responseRow.getUuid());
      assertSame(EExecutedOp.CREATED.toString(), responseRow.getOp());
      NakCollection collection = Base.assign(responseRow.getFeature(), NakCollection.getKlass());
      assertNotNull(collection);
      assertEquals(collectionId(), collection.getId());
      assertFalse(collection.isDisableHistory());
      assertEquals(partition(), collection.hasPartitions());
      assertNotNull(collection.getProperties());
      assertNotNull(collection.getProperties().getXyz());
      assertSame(
          EXyzAction.CREATE.toString(),
          collection.getProperties().getXyz().getAction());
    } finally {
      session.commit(true);
    }
  }

  @Test
  @Order(35)
  @EnabledIf("runTest")
  void createExistingCollection() throws NoCursor, SQLException {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteXyzCollections request = new WriteXyzCollections();
    request.add(EWriteOp.CREATE, new XyzCollection(collectionId(), partitionCount(), false, true));
    try (final ForwardCursor<XyzCollection, XyzCollectionCodec> cursor =
             session.execute(request).getXyzCollectionCursor()) {
      assertTrue(cursor.next());
      assertSame(EExecutedOp.ERROR, cursor.getOp());
      assertEquals(XyzError.CONFLICT.value(), cursor.getError().err.value());
    } finally {
      session.commit(true);
    }

    assertTrue(isLockReleased(session, collectionId()));
  }

  // Custom stuff between 50 and 9000

  @Test
  @Order(9002)
  @EnabledIf("isTestContainerRun")
  void createBrittleCollection() throws NoCursor, SQLException, IOException, InterruptedException {
    assertNotNull(storage);
    assertNotNull(session);

    // given

    // PREPARE CATALOGS IN DOCKER CONTAINER
    createCatalogsForTablespace();
    // PREPARE TABLESPACES
    createTablespace();

    // WRITE COLLECTION THAT SHOULD BE TEMPORARY
    String collectionId = "foo_temp";
    final WriteXyzCollections request = new WriteXyzCollections();
    XyzCollection xyzCollection = new XyzCollection(collectionId, 8, false, true);
    xyzCollection.setStorageClass("brittle");
    request.add(EWriteOp.CREATE, xyzCollection);

    try (final ForwardCursor<XyzCollection, XyzCollectionCodec> cursor =
             session.execute(request).getXyzCollectionCursor()) {
      assertTrue(cursor.next());
      assertNull(cursor.getError(), () -> cursor.getError().msg);
    }

    // WRITE AND UPDATE FEATURE TO CREATE _hst PARTITIONS
    XyzFeature feature = new XyzFeature();
    try (final @NotNull Result result = session.execute(createFeatureRequest(collectionId, feature))) {
      assertInstanceOf(SuccessResult.class, result);
    }
    try (final @NotNull Result result = session.execute(updateFeatureRequest(collectionId, feature))) {
      assertInstanceOf(SuccessResult.class, result);
    }
    session.commit(true);

    // then
    String expectedTablespace = ConstantsKt.getTEMPORARY_TABLESPACE();
    assertEquals(expectedTablespace, getTablespace(session, collectionId));
    assertEquals(expectedTablespace, getTablespace(session, collectionId + "$hst"));
    int currentYear = LocalDate.now().getYear();
    assertEquals(expectedTablespace, getTablespace(session, collectionId + "$hst_" + currentYear));
    assertEquals(expectedTablespace, getTablespace(session, collectionId + "$del"));
    assertEquals(expectedTablespace, getTablespace(session, collectionId + "$meta"));
    if (partition()) {
      assertEquals(expectedTablespace, getTablespace(session, collectionId + "$hst_" + currentYear + "_p000"));
      assertEquals(expectedTablespace, getTablespace(session, collectionId + "$del_p000"));
      assertEquals(expectedTablespace, getTablespace(session, collectionId + "$p000"));
    }
  }

  private String getTablespace(PsqlWriteSession session, String table) throws SQLException {
    try (PreparedStatement statement = session.session().prepareStatement("select tablespace from pg_tables where tablename=?")) {
      statement.setString(1, table);
      ResultSet resultSet = statement.executeQuery();
      assertTrue(resultSet.next(), () -> "no table found: " + table);
      return resultSet.getString(1);
    }
  }

  private void createCatalogsForTablespace() throws IOException, InterruptedException {
    postgreSQLContainer.execInContainer("mkdir", "-p", "/tmp/temporary_space");
    postgreSQLContainer.execInContainer("chown", "postgres:postgres", "-R", "/tmp/temporary_space");
  }

  private void createTablespace() throws IOException, InterruptedException {
    postgreSQLContainer.execInContainer("psql", "-U", "postgres", "-d", "postgres", "-c", format("create tablespace %s LOCATION '/tmp/temporary_space';", ConstantsKt.getTEMPORARY_TABLESPACE()));
  }

  private boolean isLockReleased(PsqlWriteSession session, String collectionId) throws SQLException {
    final PostgresSession pgSession = session.session();
    final SQL sql = pgSession.sql().add("select count(*) from pg_locks where locktype = 'advisory' and ((classid::bigint << 32) | objid::bigint) = ?;");
    try (PreparedStatement stmt = pgSession.prepareStatement(sql)) {
      stmt.setLong(1, new JvmBigInt64Api().toLong(Static.lockId(collectionId)));
      ResultSet resultSet = stmt.executeQuery();
      resultSet.next();
      return resultSet.getInt(1) == 0;
    }
  }

  int partitionCount() {
    return partition()? 8: 1;
  }
}
