package naksha.psql

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import naksha.model.request.ErrorResponse
import naksha.model.request.Write
import naksha.model.request.WriteOp
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_WRITER_DB_URL", matches = ".+")
class PgWriterOrderingRegressionTest : PgTestBase(collection = null) {
  init {
    assertEquals(System.getenv("NAKSHA_TEST_WRITER_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Writer regressions and bootstrap must use the same disposable database")
  }

  @Test
  fun mixedOperationRunsExecuteAndRollbackTogether() {
    for (history in listOf(false, true)) {
      useCollection(NakshaCollection("writer_mixed_$history", catalog.id).apply {
        storeHistory = if (history) StoreMode.ON else StoreMode.OFF
      })
      val ids = orderedIds("mixed", 6)
      val seed = WriteRequest()
      for (i in 0 until 6) {
        if (i != 2) seed.add(Write().createFeature(collection, NakshaFeature(ids[i])))
      }
      executeWrite(seed)
      executeWrite(WriteRequest().add(Write().deleteFeatureById(collection, ids[1])))
      val before = snapshot(collection)
      val request = WriteRequest()
        .add(Write().updateFeature(collection, NakshaFeature(ids[0]), false))
        .add(Write().purgeFeatureById(collection, ids[1]))
        .add(Write().createFeature(collection, NakshaFeature(ids[2])))
        .add(Write().deleteFeatureById(collection, ids[3]))
        .add(Write().upsertFeature(collection, NakshaFeature(ids[4]), false))
        .add(Write().createFeature(collection, NakshaFeature(ids[5])))
      assertTrue(executeWriteErrorResponse(request).error.isConflict())
      assertEquals(before, snapshot(collection), "A late CREATE conflict must restore every earlier operation and history row")
      storage.newWriteSession(newSessionOptions()).use { session ->
        assertSuccess(session.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("earlier_request")))))
        val error = session.executeWrite(request) as ErrorResponse
        assertTrue(error.error.isConflict())
        session.commit()
      }
      assertTrue(read(collection).containsKey("earlier_request"), "Request savepoint rollback must preserve earlier successful writes")
      executeWrite(WriteRequest().add(Write().deleteFeatureById(collection, "earlier_request")))

      request.writes[5] = Write().updateFeature(collection, NakshaFeature(ids[5]), false)
      val response = executeWrite(request)
      assertNotNull(response.featureTupleList[0])
      assertNull(response.featureTupleList[1])
      for (i in 2 until 6) assertEquals(ids[i], assertNotNull(response.featureTupleList[i]).tuple?.id?.text)
      val read = read(collection)
      assertEquals(4, read.size)
      assertFalse(read.containsKey(ids[1]))
      assertFalse(read.containsKey(ids[3]))
      assertTrue(read.containsKey(ids[2]))
    }
  }

  @Test
  fun atomicDeleteWithoutUuidRejectsFeatureAndMetadataWithoutDataLoss() {
    useCollection("writer_missing_uuid")
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("kept"))))
    val before = snapshot(collection)
    val writes = listOf(
      Write().deleteFeature(collection, NakshaFeature("kept"), true),
      Write().deleteCollection(NakshaCollection(collection.id, catalog.id), true),
      Write().deleteCatalog(NakshaCatalog(storage.id, catalog.id), true)
    )
    for (write in writes) {
      assertTrue(write.atomic)
      assertNull(write.version)
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, executeWriteErrorResponse(WriteRequest().add(write)).error.code)
      assertEquals(before, snapshot(collection))
      assertEquals(1, read(collection).size)
    }
  }

  @Test
  fun collectionDropRollsBackPhysicalObjectsAndRemovesRegistryOnCommit() {
    useCollection("writer_collection_drop")
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("kept"))))
    val before = snapshot(collection)
    assertTrue(before[1].isNotEmpty(), "History collection must be registered")
    val drop = WriteRequest().add(Write().deleteCollectionById(catalog.id, collection.id))
    assertTrue(executeWriteErrorResponse(WriteRequest()
      .add(Write().deleteCollectionById(catalog.id, collection.id))
      .add(Write().createFeature(collection, NakshaFeature("kept")))).error.isConflict())
    assertEquals(before, snapshot(collection), "A failed logical batch must not drop physical data")
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(drop))
      session.rollback()
    }
    assertEquals(before, snapshot(collection), "Outer rollback must restore root/child/index OIDs, registry and data")
    assertEquals(1, read(collection).size)
    executeWrite(drop)
    assertEquals(listOf(emptyList(), emptyList(), emptyList()), snapshot(collection))
    assertTrue(readMetadata(catalog.id, Id.COLLECTIONS_COL_ID.text, collection.id).isEmpty())
  }

  @Test
  fun catalogDropRollsBackPhysicalObjectsAndRemovesRegistryOnCommit() {
    val target = NakshaCatalog(storage.id, "writer_catalog_drop")
    val targetCollection = NakshaCollection("features", target.id)
    executeWrite(WriteRequest()
      .add(Write().createCatalog(target))
      .add(Write().createCollection(targetCollection))
      .add(Write().createFeature(targetCollection, NakshaFeature("kept"))))
    val before = snapshot(targetCollection)
    val schema = Id(target.id).catalogName()
    val registryBefore = registrations(schema)
    assertTrue(registryBefore.isNotEmpty())
    assertTrue(executeWriteErrorResponse(WriteRequest()
      .add(Write().deleteCatalogById(target.id))
      .add(Write().createFeature(targetCollection, NakshaFeature("kept")))).error.isConflict())
    assertEquals(before, snapshot(targetCollection))
    assertEquals(registryBefore, registrations(schema))
    // Collection preparation takes a shared catalog guard; DELETE must take its exclusive guard first.
    val drop = WriteRequest()
      .add(Write().createCollection(NakshaCollection("prepared_then_dropped", target.id)))
      .add(Write().deleteCatalogById(target.id))
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(drop))
      session.rollback()
    }
    assertEquals(before, snapshot(targetCollection))
    assertEquals(registryBefore, registrations(schema))
    assertEquals(1, read(targetCollection).size)
    executeWrite(drop)
    assertEquals(listOf(emptyList(), emptyList(), emptyList()), snapshot(targetCollection))
    assertTrue(registrations(schema).isEmpty())
    assertTrue(readMetadata(Id.ADMIN_CATALOG_ID.text, Id.CATALOGS_COL_ID.text, target.id).isEmpty())
    storage.adminConnection().use { conn ->
      conn.execute("SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = $1", arrayOf(schema)).use { cursor ->
        assertFalse(cursor.next())
      }
    }
  }

  @Test
  fun builtInDeletionIsRejectedBeforeLocksAndPreservesMetadata() {
    useCollection("writer_builtin_preservation")
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("kept"))))
    val targets = listOf(
      NakshaCollection(Id.COLLECTIONS_COL_ID.text, catalog.id),
      NakshaCollection(Id.COLLECTIONS_COL_ID.text, Id.ADMIN_CATALOG_ID.text),
      NakshaCollection(Id.CATALOGS_COL_ID.text, Id.ADMIN_CATALOG_ID.text),
      NakshaCollection(Id.TRANSACTIONS_COL_ID.text, Id.ADMIN_CATALOG_ID.text),
      NakshaCollection(Id.BOOKS_COL_ID.text, Id.ADMIN_CATALOG_ID.text)
    )
    val before = ArrayList<List<List<String>>>()
    for (target in targets) before.add(snapshot(target))
    val writes = ArrayList<Write>()
    writes.add(Write().deleteCatalogById(Id.ADMIN_CATALOG_ID.text))
    writes.add(Write().deleteCatalogById(Id.ADMIN_CATALOG_ID.text).apply { op = WriteOp.PURGE })
    for (target in targets) {
      writes.add(Write().deleteCollectionById(target.catalogId, target.id))
      writes.add(Write().deleteCollectionById(target.catalogId, target.id).apply { op = WriteOp.PURGE })
    }
    for (write in writes) {
      storage.newWriteSession(newSessionOptions()).use { writeSession ->
        val session = writeSession as PgSession
        assertEquals(NakshaError.FORBIDDEN, (session.executeWrite(WriteRequest().add(write)) as ErrorResponse).error.code)
        assertNull(session.pgConnection, "Built-in rejection must precede connection allocation and locking")
        session.commit()
      }
      for (i in targets.indices) assertEquals(before[i], snapshot(targets[i]))
      assertEquals(1, read(collection).size)
    }
    useCollection("writer_builtin_still_writable")
    executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("after_rejection"))))
    assertEquals(1, read(collection).size)
  }

  @Test
  fun oppositeDeleteUpsertBatchesAcquireCombinedGuardsInTheSameOrder() {
    val executor = Executors.newFixedThreadPool(2)
    try {
      for (catalogWork in listOf(false, true)) {
        val ids = orderedIds(if (catalogWork) "writer_guard_catalog" else "writer_guard_collection", 2)
        val definitions = ArrayList<NakshaCollection>()
        for (id in ids) {
          val targetCatalog = if (catalogWork) id else catalog.id
          if (catalogWork) executeWrite(WriteRequest().add(Write().createCatalog(NakshaCatalog(storage.id, id))))
          val target = NakshaCollection(if (catalogWork) "features" else id, targetCatalog)
          executeWrite(WriteRequest().add(Write().createCollection(target))
            .add(Write().createFeature(target, NakshaFeature("kept"))))
          definitions.add(target)
        }
        val before = ArrayList<List<List<String>>>()
        for (target in definitions) before.add(snapshot(target))
        val requests = ArrayList<WriteRequest>()
        for (i in 0..1) {
          val other = 1 - i
          val request = WriteRequest()
          if (catalogWork) {
            request.add(Write().deleteCatalogById(ids[i]))
              .add(Write().upsertCatalog(NakshaCatalog(storage.id, ids[other]), false))
          } else {
            request.add(Write().deleteCollectionById(catalog.id, ids[i]))
              .add(Write().upsertCollection(NakshaCollection(ids[other], catalog.id), false))
          }
          requests.add(request)
        }
        storage.adminConnection().use { blocker ->
          val schema = Id(if (catalogWork) ids[0] else catalog.id).catalogName()
          if (catalogWork) PgPartitions.lockCatalog(blocker, schema)
          else PgPartitions.lockCollection(blocker, schema, Id(ids[0]).collectionName())
          storage.newWriteSession(newSessionOptions()).use { firstWrite ->
            storage.newWriteSession(newSessionOptions()).use { secondWrite ->
              val sessions = listOf(firstWrite as PgSession, secondWrite as PgSession)
              val pids = ArrayList<String>()
              for (session in sessions) {
                session.useTransaction()
                pids.add(assertNotNull(session.useConn(false) { conn ->
                  conn.execute("SET LOCAL lock_timeout = '15s'; SET LOCAL statement_timeout = '20s'").close()
                  conn.execute("SELECT pg_catalog.pg_backend_pid()::text AS pid").fetch().use { cursor -> cursor.get<String>("pid") }
                }))
              }
              val first = executor.submit(Callable {
                try { sessions[0].executeWrite(requests[0]) } finally { sessions[0].rollback() }
              })
              val second = executor.submit(Callable {
                try { sessions[1].executeWrite(requests[1]) } finally { sessions[1].rollback() }
              })
              try {
                storage.adminConnection().use { observer ->
                  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                  var waiting = false
                  while (System.nanoTime() < deadline) {
                    waiting = observer.execute("SELECT count(*) = 2 AS waiting FROM pg_catalog.pg_stat_activity WHERE pid IN ($1::int, $2::int) AND wait_event_type = 'Lock' AND wait_event = 'advisory'",
                      arrayOf(pids[0], pids[1])).fetch().use { cursor -> cursor.get<Boolean>("waiting") }
                    if (waiting) break
                    Thread.sleep(10)
                  }
                  assertTrue(waiting, "Both writers must queue on the lowest combined guard")
                  val highSchema = Id(if (catalogWork) ids[1] else catalog.id).catalogName()
                  val unlocked = if (catalogWork) PgPartitions.lockCatalog(observer, highSchema, tryOnly = true)
                    else PgPartitions.lockCollection(observer, highSchema, Id(ids[1]).collectionName(), tryOnly = true)
                  assertTrue(unlocked, "Neither writer may acquire its DELETE subset ahead of the combined order")
                  observer.rollback()
                }
              } finally {
                blocker.rollback()
                assertSuccess(first.get(20, TimeUnit.SECONDS))
                assertSuccess(second.get(20, TimeUnit.SECONDS))
              }
            }
          }
        }
        for (i in definitions.indices) {
          assertEquals(before[i], snapshot(definitions[i]))
          assertEquals(1, read(definitions[i]).size)
        }
      }
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun mixedCatalogWorkKeepsExistingReuseSharedForIndependentCollections() {
    val ids = orderedIds("writer_scoped_guard", 2)
    for (id in ids) executeWrite(WriteRequest().add(Write().createCatalog(NakshaCatalog(storage.id, id))))
    val executor = Executors.newSingleThreadExecutor()
    try {
      for (delete in listOf(false, true)) {
        val request = WriteRequest()
        request.add(if (delete) Write().deleteCatalogById(ids[0])
          else Write().upsertCatalog(NakshaCatalog(storage.id, ids[0]), false))
        request.add(Write().upsertCatalog(NakshaCatalog(storage.id, ids[1]), false))
        storage.newWriteSession(newSessionOptions()).use { session ->
          assertSuccess(session.executeWrite(request))
          try {
            val future = executor.submit(Callable {
              storage.newWriteSession(newSessionOptions()).use { independent ->
                val pgSession = independent as PgSession
                pgSession.useConn(false) { conn ->
                  conn.execute("SET LOCAL lock_timeout = '3s'; SET LOCAL statement_timeout = '5s'").close()
                }
                try {
                  independent.executeWrite(WriteRequest().add(Write().upsertCollection(
                    NakshaCollection("independent_$delete", ids[1]), false)))
                } finally {
                  independent.rollback()
                }
              }
            })
            assertSuccess(future.get(10, TimeUnit.SECONDS))
          } finally {
            session.rollback()
          }
        }
      }
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun blockedLowestIdDoesNotLockHigherIdsAcrossOperationsOrAtomicity() {
    useCollection(NakshaCollection("writer_lock_order", catalog.id).apply { storeHistory = StoreMode.OFF })
    val ids = orderedIds("lock", 2)
    executeWrite(WriteRequest()
      .add(Write().createFeature(collection, NakshaFeature(ids[0])))
      .add(Write().createFeature(collection, NakshaFeature(ids[1]))))
    val low = assertNotNull(read(collection)[ids[0]])
    val requests = listOf(
      WriteRequest().add(Write().deleteFeatureById(collection, ids[1]))
        .add(Write().updateFeature(collection, NakshaFeature(ids[0]), false)),
      WriteRequest().add(Write().deleteFeatureById(collection, ids[1]))
        .add(Write().deleteFeature(collection, low, true))
    )
    val executor = Executors.newSingleThreadExecutor()
    val relation = relation(collection)
    try {
      for (request in requests) {
        storage.adminConnection().use { blocker ->
          blocker.execute("SELECT fn FROM $relation WHERE fn = $1::bigint AND nv = ${Version.HEAD.number} FOR UPDATE",
            arrayOf(Id.featureNumberToOrdinal(Id(ids[0]).number).toString())).close()
          storage.newWriteSession(newSessionOptions()).use { writeSession ->
            val session = writeSession as PgSession
            session.useTransaction()
            val pid = session.useConn(false) { conn ->
              conn.execute("SELECT pg_catalog.pg_backend_pid()::text AS pid").fetch().use { cursor -> cursor.get<String>("pid") }
            }
            val future = executor.submit(Callable { session.executeWrite(request) })
            try {
              storage.adminConnection().use { observer ->
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var waiting = false
                while (System.nanoTime() < deadline) {
                  waiting = observer.execute("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_stat_activity WHERE pid = $1::int AND wait_event_type = 'Lock') AS waiting",
                    arrayOf(pid)).fetch().use { cursor -> cursor.get<Boolean>("waiting") }
                  if (waiting) break
                  Thread.sleep(10)
                }
                assertTrue(waiting, "Writer must block on the lowest-ID row")
                observer.execute("SELECT fn FROM $relation WHERE fn = $1::bigint AND nv = ${Version.HEAD.number} FOR UPDATE NOWAIT",
                  arrayOf(Id.featureNumberToOrdinal(Id(ids[1]).number).toString())).close()
                observer.rollback()
              }
            } finally {
              blocker.rollback()
              assertSuccess(future.get(10, TimeUnit.SECONDS))
              session.rollback()
            }
          }
        }
      }
    } finally {
      executor.shutdownNow()
    }
    assertEquals(2, read(collection).size)
  }

  private fun orderedIds(prefix: String, count: Int): List<String> {
    val ids = ArrayList<String>(count)
    for (i in 0 until count) ids.add("${prefix}_$i")
    ids.sortWith { a, b -> Id(a).compareTo(Id(b)) }
    return ids
  }

  private fun relation(target: NakshaCollection): String =
    "${PgUtil.quoteIdent(Id(assertNotNull(target.catalogId)).catalogName())}.${PgUtil.quoteIdent(Id(target.id).collectionName())}"

  private fun read(target: NakshaCollection): Map<String, NakshaFeature> {
    val features = executeRead(ReadFeatures().apply {
      catalogId = target.catalogId
      collectionId = target.id
    }).features
    val result = HashMap<String, NakshaFeature>(features.size)
    for (feature in features) {
      if (feature != null) result[feature.id] = feature
    }
    return result
  }

  private fun readMetadata(catalog: String, collection: String, id: String) = executeRead(ReadFeatures().apply {
    catalogId = catalog
    collectionId = collection
    featureIds.add(id)
  }).features

  private fun registrations(schema: String): List<String> = storage.adminConnection().use { conn ->
    strings(conn, "SELECT row_to_json(r)::text AS value FROM \"naksha~admin\".naksha_partman r WHERE schema_name = $1 ORDER BY table_name", arrayOf(schema))
  }

  private fun snapshot(target: NakshaCollection): List<List<String>> = storage.adminConnection().use { conn ->
    val schema = Id(assertNotNull(target.catalogId)).catalogName()
    val table = Id(target.id).collectionName()
    val objects = strings(conn, "SELECT c.oid::text || ':' || c.relname AS value FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = $1 AND (c.relname = $2 OR starts_with(c.relname, $2 || '$')) ORDER BY c.oid", arrayOf(schema, table))
    val registry = strings(conn, "SELECT row_to_json(r)::text AS value FROM \"naksha~admin\".naksha_partman r WHERE schema_name = $1 AND table_name = $2", arrayOf(schema, table))
    val rows = if (objects.isEmpty()) emptyList() else strings(conn,
      "SELECT row_to_json(r)::text AS value FROM ${relation(target)} r ORDER BY fn, nv, version")
    listOf(objects, registry, rows)
  }

  private fun strings(conn: PgConnection, sql: String, args: Array<Any?> = emptyArray()): List<String> {
    val values = ArrayList<String>()
    conn.execute(sql, args).use { cursor ->
      while (cursor.next()) values.add(cursor.get<String>("value"))
    }
    return values
  }
}
