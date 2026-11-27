package naksha.psql

import naksha.base.Id
import naksha.base.NakshaException
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgMaintenanceMetadataTest : PgTestBase(collection = null) {
  @Test
  fun uncommittedCreationIsInitializedButOnlyCommittedLiveMetadataIsDiscovered() {
    val definition = NakshaCollection("metadata_visibility", catalog.id).apply { shift = 12; partitions = 3 }
    val schema = Id(catalog.id).schemaName()
    val table = Id(definition.id).tableName()
    storage.newSession(newSessionOptions(), false).use { session ->
      assertSuccess(session.executeWrite(WriteRequest().add(Write().createCollection(definition))))
      session.useConn(false) { conn ->
        assertNotNull(present(conn, definition, PgPartitions.currentVersion(conn) shr 12),
          "Creation must finish synchronous initialization before returning, not after cron runs")
      }
      connection().use { observer ->
        assertNull(PgMaintenance.find(storage.database, observer, schema, table))
        assertFalse(PgMaintenance.collections(storage.database, observer).any { it.table == table && it.catalogId == Id(catalog.id) })
      }
      session.commit()
    }
    connection().use { observer -> assertNotNull(PgMaintenance.find(storage.database, observer, schema, table)) }
    executeWrite(WriteRequest().add(Write().deleteCollectionById(catalog.id, definition.id)))
    connection().use { observer ->
      assertNull(PgMaintenance.find(storage.database, observer, schema, table), "DELETE metadata tombstones must not be interpreted as active collections")
      val version = PgPartitions.currentVersion(observer) + (16L shl 12)
      PgMaintenance.maintainAll(storage.database, observer, version)
      assertNull(present(observer, definition, version shr 12), "Maintenance must never resurrect deleted roots")
    }
  }

  @Test
  fun creationInitializesSynchronouslyWithoutAnyPartitionRegistry() {
    val definition = create("metadata_initialization", partitions = 3)
    connection().use { conn ->
      val version = PgPartitions.currentVersion(conn)
      for (slot in 0..3) assertNotNull(present(conn, definition, (version shr 12) + slot))
      assertNull(value(conn, "SELECT to_regclass('\"naksha~admin\".\"naksha~partman\"')::text AS value"))
      assertNull(value(conn, "SELECT to_regclass('\"naksha~admin\".naksha_partman')::text AS value"))
      val descriptor = assertNotNull(PgMaintenance.find(storage.database, conn,
        Id(assertNotNull(definition.catalogId)).schemaName(), Id(definition.id).tableName()))
      assertEquals(3, descriptor.partitions)
      assertEquals(12, descriptor.shift)
      val targets = PgMaintenance.collections(storage.database, conn)
      assertTrue(targets.any { it.id == Id.COLLECTIONS_COL_ID && it.catalogId == Id(catalog.id) },
        "A catalog's collections descriptor has no self-row but still needs maintenance")
      assertTrue(targets.any { it.id == Id.CATALOGS_COL_ID && it.catalogId == Id.ADMIN_CATALOG_ID })
    }
  }

  @Test
  fun sweepUsesLiveMetadataAcrossCatalogsAndDoesNotAdvanceTheAllocator() {
    val first = create("metadata_sweep_a", partitions = 3)
    val off = create("metadata_sweep_off", history = false)
    val other = initCatalog("naksha_psql_other")
    useCollection(NakshaCollection("metadata_sweep_b", other.id).apply { shift = 12; partitions = 1 })
    val second = collection
    connection().use { conn ->
      val version = PgPartitions.currentVersion(conn) + (32L shl 12)
      val before = allocator(conn)
      val expected = PgMaintenance.collections(storage.database, conn).count { it.storeHistory }
      assertTrue(expected >= 7)
      assertNull(present(conn, first, version shr 12))
      assertEquals(expected, PgMaintenance.maintainAll(storage.database, conn, version))
      for (definition in listOf(first, second)) {
        for (slot in 0..3) assertNotNull(present(conn, definition, (version shr 12) + slot))
      }
      assertNull(present(conn, off, version shr 12))
      assertEquals(expected, PgMaintenance.maintainAll(storage.database, conn, version))
      assertEquals(before, allocator(conn))
      conn.rollback()
      assertNull(present(conn, first, version shr 12), "Caller rollback must remove sweep-created partitions")
    }
  }

  @Test
  fun failedSweepPreservesCallerRollbackAndDeterministicOrder() {
    val first = create("metadata_failure_a")
    val last = create("metadata_failure_z")
    connection().use { conn ->
      val version = PgPartitions.currentVersion(conn) + (64L shl 12)
      val conflict = relation(last, (version shr 12) + 2)
      conn.execute("CREATE TABLE $conflict (unrelated text)").close()
      val point = conn.jdbc.setSavepoint()
      try {
        assertFailsWith<NakshaException> { PgMaintenance.maintainAll(storage.database, conn, version) }
        assertNotNull(present(conn, first, version shr 12), "Earlier roots must be reached before the failure")
      } finally {
        conn.jdbc.rollback(point)
        conn.jdbc.releaseSavepoint(point)
      }
      assertNull(present(conn, first, version shr 12))
      conn.execute("DROP TABLE $conflict").close()
      assertTrue(PgMaintenance.maintainAll(storage.database, conn, version) > 0)
      assertNotNull(present(conn, last, version shr 12))
    }
  }

  @Test
  fun noOpSweepReleasesGuardsAndDoesNotBlockReadersOrWriters() {
    val definition = create("metadata_noop")
    connection().use { conn ->
      val version = PgPartitions.currentVersion(conn)
      val expected = PgMaintenance.maintainAll(storage.database, conn, version)
      conn.commit()
      connection().use { reader ->
        reader.execute("SELECT 1 FROM ${root(definition)} LIMIT 1").close()
        conn.execute("SET LOCAL statement_timeout = '3s'").close()
        assertEquals(expected, PgMaintenance.maintainAll(storage.database, conn, version))
        assertEquals("0", value(conn, "SELECT count(*)::text AS value FROM pg_locks WHERE pid = pg_backend_pid() AND locktype = 'advisory'"),
          "No-op attempts must not retain even shared advisory guards")
        assertEquals("0", value(conn, "SELECT count(*)::text AS value FROM pg_locks WHERE pid = pg_backend_pid() AND mode = 'AccessExclusiveLock'"))
        assertTrue(PgPartitions.lockCollection(reader, Id(assertNotNull(definition.catalogId)).schemaName(), Id(definition.id).tableName(), tryOnly = true),
          "An idle sweep must not prevent a later writer/DDL guard")
      }
    }
  }

  @Test
  fun busyCollectionIsSkippedWhileIndependentCollectionsProgress() {
    val busy = create("metadata_busy_a")
    val free = create("metadata_busy_b")
    val executor = Executors.newSingleThreadExecutor()
    try {
      connection().use { holder ->
        PgPartitions.lockCollection(holder, Id(assertNotNull(busy.catalogId)).schemaName(), Id(busy.id).tableName())
        val result = executor.submit(Callable {
          connection().use { worker ->
            worker.execute("SET LOCAL statement_timeout = '3s'").close()
            val version = PgPartitions.currentVersion(worker) + (96L shl 12)
            val expected = PgMaintenance.collections(storage.database, worker).count { it.storeHistory }
            val count = PgMaintenance.maintainAll(storage.database, worker, version)
            assertEquals(expected - 1, count)
            assertNull(present(worker, busy, version shr 12))
            assertNotNull(present(worker, free, version shr 12))
            assertFalse(worker.autoCommit)
            assertEquals("1", value(worker, "SELECT '1'::text AS value"))
          }
        })
        result.get(10, TimeUnit.SECONDS)
      }
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }
  }

  @Test
  fun installedPlv8EntryPointDiscoversAndRepairsTheSameMetadataTargets() {
    val definition = create("metadata_plv8", partitions = 3)
    connection().use { conn ->
      val version = PgPartitions.currentVersion(conn)
      val missing = relation(definition, (version shr 12) + 2)
      val before = allocator(conn)
      conn.execute("DROP TABLE $missing").close()
      val expected = PgMaintenance.collections(storage.database, conn).count { it.storeHistory }
      assertEquals(expected.toString(), value(conn, "SELECT \"naksha~admin\".naksha_maintain()::text AS value"))
      assertNotNull(present(conn, definition, (version shr 12) + 2))
      assertEquals(expected, storage.database.maintain_partitions(conn))
      assertEquals(before, allocator(conn))
    }
  }

  private fun create(name: String, history: Boolean = true, partitions: Int = 1): NakshaCollection {
    useCollection(NakshaCollection(name, catalog.id).apply {
      shift = 12
      this.partitions = partitions
      storeHistory = if (history) StoreMode.ON else StoreMode.OFF
    })
    return collection
  }

  private fun connection() = storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT)
  private fun root(definition: NakshaCollection) = "${PgUtil.quoteIdent(Id(assertNotNull(definition.catalogId)).schemaName())}.${PgUtil.quoteIdent(Id(definition.id).tableName())}"
  private fun relation(definition: NakshaCollection, number: Long) = "${PgUtil.quoteIdent(Id(assertNotNull(definition.catalogId)).schemaName())}.${PgUtil.quoteIdent(Id(definition.id).tableName() + "$" + number)}"
  private fun present(conn: PgConnection, definition: NakshaCollection, number: Long) = value(conn, "SELECT to_regclass(${PgUtil.quoteLiteral(relation(definition, number))})::text AS value")
  private fun allocator(conn: PgConnection) = value(conn, "SELECT ROW(last_value, is_called)::text AS value FROM \"naksha~admin\".${PgUtil.quoteIdent(NAKSHA_VERSION_SEQ)}")
  private fun value(conn: PgConnection, sql: String): String? = conn.execute(sql).fetch().use { it.column("value") as String? }
}
