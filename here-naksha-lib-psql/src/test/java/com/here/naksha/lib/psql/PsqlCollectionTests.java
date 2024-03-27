package com.here.naksha.lib.psql;

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
  void createCollection() throws NoCursor {
    assertNotNull(storage);
    assertNotNull(session);
    final WriteXyzCollections request = new WriteXyzCollections();
    request.add(EWriteOp.CREATE, new XyzCollection(collectionId(), partition(), false, true));
    try (final ForwardCursor<XyzCollection, XyzCollectionCodec> cursor =
             session.execute(request).getXyzCollectionCursor()) {
      assertNotNull(cursor);
      assertTrue(cursor.hasNext());
      assertTrue(cursor.next());
      assertEquals(collectionId(), cursor.getId());
      assertNotNull(cursor.getUuid());
      assertNull(cursor.getGeometry());
      assertSame(EExecutedOp.CREATED, cursor.getOp());
      final XyzCollection collection = cursor.getFeature();
      assertNotNull(collection);
      assertEquals(collectionId(), collection.getId());
      assertFalse(collection.pointsOnly());
      if (partition()) {
        assertTrue(collection.isPartitioned());
      } else {
        assertFalse(collection.isPartitioned());
      }
      assertNotNull(collection.getProperties());
      assertNotNull(collection.getProperties().getXyzNamespace());
      assertSame(
          EXyzAction.CREATE,
          collection.getProperties().getXyzNamespace().getAction());
      assertFalse(cursor.hasNext());
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
    request.add(EWriteOp.CREATE, new XyzCollection(collectionId(), partition(), false, true));
    try (final ForwardCursor<XyzCollection, XyzCollectionCodec> cursor =
             session.execute(request).getXyzCollectionCursor()) {
      assertTrue(cursor.next());
      assertEquals(collectionId(), cursor.getId());
      assertNotNull(cursor.getUuid());
      assertNull(cursor.getGeometry());
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
  void createCollectionInArena() throws NoCursor, SQLException, IOException, InterruptedException {
    assertNotNull(storage);
    assertNotNull(session);

    // given
    String arenaId = "x";

    // PREPARE CATALOGS IN DOCKER CONTAINER
    createCatalogsForTablespaces(arenaId);
    // PREPARE TABLESPACES
    createTablespaces(arenaId);


    // WRITE COLLECTION THAT SHOULD BE ASSIGNED TO ARENA TABLESPACE
    String collectionId = "foo_in_arena";
    final WriteXyzCollections request = new WriteXyzCollections();
    XyzCollection xyzCollection = new XyzCollection(collectionId, true, false, true);
    xyzCollection.setArenaId(arenaId);
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
    assertEquals("naksha_x_main", getTablespace(session, collectionId));
    assertEquals("naksha_x_head_0", getTablespace(session, collectionId + "_p0"));
    assertEquals("naksha_x_main", getTablespace(session, collectionId + "_hst"));
    int currentYear = LocalDate.now().getYear();
    assertEquals("naksha_x_main", getTablespace(session, collectionId + "_hst_" + currentYear));
    assertEquals("naksha_x_hst_0", getTablespace(session, collectionId + "_hst_" + currentYear + "_0"));
  }

  private String getTablespace(PsqlWriteSession session, String table) throws SQLException {
    try (PreparedStatement statement = session.session().prepareStatement("select tablespace from pg_tables where tablename=?")) {
      statement.setString(1, table);
      ResultSet resultSet = statement.executeQuery();
      assertTrue(resultSet.next(), () -> "no table found: " + table);
      return resultSet.getString(1);
    }
  }

  private void createCatalogsForTablespaces(String arenaId) throws IOException, InterruptedException {
    postgreSQLContainer.execInContainer("mkdir", "-p", format("/%s/main",arenaId));

    for (int i = 0; i < 8; i++) {
      postgreSQLContainer.execInContainer("mkdir", "-p", format("/x/hst%s",i));
      postgreSQLContainer.execInContainer("mkdir", "-p", format("/x/head%s",i));
    }
    postgreSQLContainer.execInContainer("chown", "postgres:postgres", "-R", format("/%s", arenaId));
  }

  private void createTablespaces(String arenaId) throws IOException, InterruptedException {
    postgreSQLContainer.execInContainer("psql", "-U", "postgres", "-d", "postgres", "-c", format("create tablespace naksha_%s_main LOCATION '/%s/main';", arenaId, arenaId));
    for (int i = 0; i < 8; i++) {
      postgreSQLContainer.execInContainer("psql", "-U", "postgres", "-d", "postgres", "-c",  format("create tablespace naksha_%s_head_%s LOCATION '/%s/head%s';", arenaId, i, arenaId, i));
      postgreSQLContainer.execInContainer("psql", "-U", "postgres", "-d", "postgres", "-c",  format("create tablespace naksha_%s_hst_%s LOCATION '/%s/hst%s';", arenaId, i, arenaId, i));
    }
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
}
