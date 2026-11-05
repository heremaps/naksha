package naksha.psql

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Repeat with the same environment and a fresh disposable database for each implementation. */
@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_COVERAGE_DB_URL", matches = ".+")
class PgWriterCoverageTest : PgTestBase(collection = null) {
  init {
    assertEquals(System.getenv("NAKSHA_TEST_COVERAGE_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
  }

  @Test
  fun coveredCreateBenchmark() {
    val executor = Executors.newFixedThreadPool(2)
    try {
      for (partitions in listOf(1, 7, 16)) {
        useCollection(NakshaCollection("coverage_bench_$partitions", catalog.id).apply {
          this.partitions = partitions
          storeHistory = StoreMode.ON
          shift = 41
        })
        // Include session allocation, version allocation, one CREATE request and commit.
        val samples = ArrayList<Double>()
        for (i in 0 until 40) {
          val start = System.nanoTime()
          executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("sample_$i"))))
          if (i >= 10) samples.add((System.nanoTime() - start) / 1e6)
        }
        samples.sort()
        println("COVERAGE_BENCH partitions=$partitions n=${samples.size} median_ms=${samples[15]} p95_ms=${samples[28]}")
        val readConnection = java.sql.DriverManager.getConnection(System.getenv("NAKSHA_TEST_COVERAGE_DB_URL"))
        val independent = storage.newWriteSession(newSessionOptions()) as PgSession
        independent.useTransaction()
        storage.newWriteSession(newSessionOptions()).use { held ->
          assertSuccess(held.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("held")))))
          val reader = executor.submit(Callable {
            readConnection.use { conn ->
              conn.createStatement().use { it.execute("SET statement_timeout = '3s'") }
              try {
                conn.createStatement().use { it.execute("SELECT count(*) FROM ${relation()}") }
                true
              } catch (_: Exception) { false }
            }
          })
          val writer = executor.submit(Callable {
            independent.use {
              independent.useConn(false) { conn ->
                conn.execute("SET LOCAL lock_timeout = '3s'; SET LOCAL statement_timeout = '5s'").close()
              }
              try {
                independent.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("disjoint")))) !is naksha.model.request.ErrorResponse
              } finally { independent.rollback() }
            }
          })
          try {
            val readProgress = reader.get(10, TimeUnit.SECONDS)
            val writeProgress = writer.get(10, TimeUnit.SECONDS)
            println("COVERAGE_PROGRESS partitions=$partitions reader=$readProgress disjoint_writer=$writeProgress held_transaction=true")
            if (System.getenv("NAKSHA_TEST_COVERAGE_ASSERT_PROGRESS") == "true") {
              assertTrue(readProgress)
              assertTrue(writeProgress)
            }
          } finally { held.rollback() }
        }
      }
    } finally { executor.shutdownNow() }
  }

  @Test
  fun deletedMetadataTargetsCanBeDeletedAgainAndRecreated() {
    val targetCatalog = NakshaCatalog(storage.id, "coverage_metadata_lifecycle")
    val target = NakshaCollection("features", targetCatalog.id)
    executeWrite(WriteRequest().add(Write().createCatalog(targetCatalog)).add(Write().createCollection(target)))
    executeWrite(WriteRequest().add(Write().deleteCollectionById(targetCatalog.id, target.id)))
    storage.newReadSession(newSessionOptions()).use { session ->
      assertNull(session.getCollectionById(targetCatalog, target.id))
      assertNotNull(session.getCollectionById(targetCatalog, target.id, allowTombstone = true))
    }
    executeWrite(WriteRequest().add(Write().deleteCollectionById(targetCatalog.id, target.id)))
    executeWrite(WriteRequest().add(Write().createCollection(NakshaCollection(target.id, targetCatalog.id))))
    executeWrite(WriteRequest().add(Write().createFeature(target, NakshaFeature("recreated_collection"))))
    executeWrite(WriteRequest().add(Write().deleteCatalogById(targetCatalog.id)))
    executeWrite(WriteRequest().add(Write().deleteCatalogById(targetCatalog.id)))
    executeWrite(WriteRequest().add(Write().createCatalog(NakshaCatalog(storage.id, targetCatalog.id)))
      .add(Write().createCollection(NakshaCollection(target.id, targetCatalog.id)))
      .add(Write().createFeature(target, NakshaFeature("recreated_catalog"))))
    storage.newReadSession(newSessionOptions()).use { session ->
      val recreatedCatalog = assertNotNull(session.getCatalogById(targetCatalog.id))
      assertNotNull(session.getCollectionById(recreatedCatalog, target.id))
    }
  }

  @Test
  fun updateProvisionsBeyondInitialFiniteCoverage() = mutationBeyondInitialCoverage("UPDATE")

  @Test
  fun upsertProvisionsBeyondInitialFiniteCoverage() = mutationBeyondInitialCoverage("UPSERT")

  @Test
  fun deleteProvisionsBeyondInitialFiniteCoverage() = mutationBeyondInitialCoverage("DELETE")

  @Test
  fun purgeProvisionsBeyondInitialFiniteCoverage() = mutationBeyondInitialCoverage("PURGE")

  private fun mutationBeyondInitialCoverage(op: String) {
    useCollection(NakshaCollection("coverage_rollover_${op.lowercase()}", catalog.id).apply {
      partitions = 7
      shift = 12
      storeHistory = StoreMode.ON
    })
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature(op))))
    if (op == "PURGE") executeWrite(WriteRequest().add(Write().deleteFeatureById(collection, op)))
    storage.newWriteSession(newSessionOptions()).use { writeSession ->
      val session = writeSession as PgSession
      val tx = session.useTransaction()
      val version = ((tx.version.number shr 12) + 32) shl 12
      tx.setVersion(Version(version))
      session.useConn(false) { conn ->
        PgPartitions.lockCollection(conn, Id(catalog.id).catalogName(), Id(collection.id).collectionName(), shared = true)
        assertFalse(PgPartitions.covered(conn, Id(catalog.id).catalogName(), Id(collection.id).collectionName(), version))
      }
      val write = when (op) {
        "UPDATE" -> Write().updateFeature(collection, NakshaFeature(op), false)
        "UPSERT" -> Write().upsertFeature(collection, NakshaFeature(op), false)
        "DELETE" -> Write().deleteFeatureById(collection, op)
        else -> Write().purgeFeatureById(collection, op)
      }
      assertSuccess(session.executeWrite(WriteRequest().add(write)))
      session.useConn(false) { conn ->
        assertTrue(PgPartitions.covered(conn, Id(catalog.id).catalogName(), Id(collection.id).collectionName(), version))
        conn.execute("SELECT nv::text AS nv, tableoid::regclass::text AS leaf FROM ${relation()} WHERE fn = $1::bigint AND nv >= $version AND nv <> ${Version.HEAD.number}",
          arrayOf(Id.featureNumberToOrdinal(Id(op).number).toString())).use { cursor ->
          assertTrue(cursor.next(), "$op must archive its predecessor")
          assertEquals(version shr 2, cursor.get<String>("nv").toLong() shr 2, "Archive nv includes the operation action bits")
          assertTrue(cursor.get<String>("leaf").contains("${'$'}${version shr 12}${'$'}"))
        }
      }
      // Persist the provisioned range and archive, without involving transaction-log writes at this synthetic version.
      session.useConn(false) { it.commit() }
      session.rollback()
    }
  }

  @Test
  fun coveredWritesExcludeDropAndMaintenanceButNotOtherSharedWriters() {
    useCollection(NakshaCollection("coverage_drop_guard", catalog.id).apply {
      partitions = 7
      shift = 41
      storeHistory = StoreMode.ON
    })
    val observer = java.sql.DriverManager.getConnection(System.getenv("NAKSHA_TEST_COVERAGE_DB_URL"))
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("guarded")))))
      observer.use { conn ->
        val schema = Id(catalog.id).catalogName()
        val table = Id(collection.id).collectionName()
        conn.autoCommit = false
        conn.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtextextended(format('naksha:partman:%I.%I', ?::text, ?::text), 0))").use { statement ->
          statement.setString(1, schema)
          statement.setString(2, table)
          statement.executeQuery().use { result ->
            assertTrue(result.next())
            assertFalse(result.getBoolean(1), "Collection drop and maintenance must wait for covered writers")
          }
        }
        conn.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtextextended(?::text, 0))").use { statement ->
          statement.setString(1, "naksha:schema:$schema")
          statement.executeQuery().use { result ->
            assertTrue(result.next())
            assertFalse(result.getBoolean(1), "Catalog drop must wait for covered writers")
          }
        }
        conn.rollback()
      }
      session.rollback()
    }
  }

  @Test
  fun missingLeafUsesMaintainerAndRepairsCoverage() {
    useCollection(NakshaCollection("coverage_missing_leaf", catalog.id).apply {
      partitions = 7
      shift = 41
      storeHistory = StoreMode.ON
    })
    storage.adminConnection().use { conn ->
      val schema = Id(catalog.id).catalogName()
      val table = Id(collection.id).collectionName()
      val version = PgPartitions.currentVersion(conn)
      conn.execute("DROP TABLE ${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(table + "${'$'}${version shr 41}${'$'}6")}").close()
      conn.commit()
    }
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("repaired"))))
    storage.adminConnection().use { conn ->
      assertTrue(PgPartitions.covered(conn, Id(catalog.id).catalogName(), Id(collection.id).collectionName(), PgPartitions.currentVersion(conn)))
    }
  }

  @Test
  fun coverageDoesNotLockUnneededRetainedHistory() {
    useCollection(NakshaCollection("coverage_retained_lock", catalog.id).apply {
      partitions = 7
      shift = 41
      storeHistory = StoreMode.ON
    })
    val schema = Id(catalog.id).catalogName()
    val table = Id(collection.id).collectionName()
    storage.adminConnection().use { conn ->
      PgPartitions.maintain(conn, schema, table, 0)
      conn.commit()
      java.sql.DriverManager.getConnection(System.getenv("NAKSHA_TEST_COVERAGE_DB_URL")).use { blocker ->
        blocker.autoCommit = false
        blocker.createStatement().use { statement ->
          statement.execute("LOCK TABLE ONLY ${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(table + "${'$'}0${'$'}0")} IN ACCESS EXCLUSIVE MODE")
        }
        try {
          conn.execute("SET LOCAL statement_timeout = '3s'").close()
          PgPartitions.lockCollection(conn, schema, table, shared = true)
          assertTrue(PgPartitions.covered(conn, schema, table, PgPartitions.currentVersion(conn)),
            "Required coverage must not lock or deparse unrelated retained leaves")
        } finally {
          conn.rollback()
          blocker.rollback()
        }
      }
    }
  }

  @Test
  fun realTableoidRoutingAtEveryFnBoundaryAndClippedHead() {
    storage.adminConnection().use { conn ->
      val schema = Id(catalog.id).catalogName()
      val head = Version.HEAD.number
      try {
        for (partitions in listOf(1, 7, 256)) {
          val table = "coverage_route_$partitions"
          storage.database.collection_create_table_if_not_exists(conn, schema, table,
            arrayOf(PgColumn.FnColumn, PgColumn.VersionColumn, PgColumn.NextVersionColumn), emptyArray(),
            PgCollectionCreateOptions(storeHistory = false, partitions = partitions, shift = 41, retain = 10,
              backward = -1, forward = 3, storageClass = PgStorageClass.Consistent, tablespace = null, toastTupleTarget = 2048),
            catalog.id, table)
          PgPartitions.register(conn, schema, table, 41, partitions, 2048, 50, 100, null)
          PgPartitions.maintain(conn, schema, table, head - 1)
          assertTrue(PgPartitions.covered(conn, schema, table, head - 1))
          val root = "${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(table)}"
          for (slot in 0 until partitions) {
            val low = if (slot == 0) Long.MIN_VALUE else Id.partitionIndexMin(slot, partitions)
            val high = if (slot == partitions - 1) Long.MAX_VALUE else Id.partitionIndexMin(slot + 1, partitions) - 1
            for (fn in listOf(low, high)) {
              for (nv in listOf(head - 1, head)) {
                val child = table + (if (nv == head) "${'$'}head" else "${'$'}4095") + (if (partitions == 1) "" else "${'$'}$slot")
                conn.execute("INSERT INTO $root (fn, version, nv) VALUES ($fn, 0, $nv) RETURNING tableoid = $1::regclass AS routed",
                  arrayOf("${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(child)}")).fetch().use { cursor ->
                  assertTrue(cursor.get<Boolean>("routed"), "fn=$fn nv=$nv partitions=$partitions slot=$slot")
                }
              }
            }
          }
          // A fast coverage check is not a substitute for explicit maintenance integrity validation.
          conn.execute("ALTER TABLE ${PgUtil.quoteIdent(schema)}.${PgUtil.quoteIdent(table + "${'$'}head" + if (partitions == 1) "" else "${'$'}0")} SET (fillfactor = 70)").close()
          assertTrue(PgPartitions.covered(conn, schema, table, head - 1))
          val savepoint = conn.jdbc.setSavepoint()
          try {
            assertFailsWith<NakshaException> { PgPartitions.maintain(conn, schema, table, head - 1) }
          } finally { conn.jdbc.rollback(savepoint) }
        }
      } finally { conn.rollback() }
    }
  }

  private fun relation(): String =
    "${PgUtil.quoteIdent(Id(catalog.id).catalogName())}.${PgUtil.quoteIdent(Id(collection.id).collectionName())}"
}
