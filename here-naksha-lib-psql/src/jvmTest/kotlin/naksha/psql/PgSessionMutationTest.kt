package naksha.psql

import naksha.base.Version
import naksha.base.Id
import naksha.model.Naksha
import naksha.model.Tuple
import naksha.model.TupleHeapCache
import naksha.model.request.FeatureTuple
import naksha.model.request.FeatureTupleList
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StoreMode
import naksha.model.request.ErrorResponse
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.*

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_SESSION_DB_URL", matches = ".+")
class PgSessionMutationTest : PgTestBase(collection = null) {
  init {
    assertEquals(System.getenv("NAKSHA_TEST_SESSION_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Session regressions and bootstrap must use the same dedicated database")
  }

  @Test
  fun warmCreateResponseDoesNotHideUpdateSuccessor() = warmHistory(false)

  @Test
  fun warmCreateResponseDoesNotHideDeleteSuccessor() = warmHistory(true)

  private fun warmHistory(delete: Boolean) {
    useCollection("session_warm_$delete")
    val created = executeWrite(request(Write().createFeature(collection, feature("kept", "created"))))
    val retained = assertNotNull(created.featureTupleList.single()?.tuple)
    assertSame(retained, Naksha.cache[retained.tupleNumber])
    assertEquals(Version.HEAD.number, retained.nextVersion)
    storage.newWriteSession(newSessionOptions()).use { session ->
      val mutation = if (delete) Write().deleteFeatureById(collection, "kept")
        else Write().updateFeature(collection, feature("kept", "updated"), false)
      val changed = assertSuccess(session.executeWrite(request(mutation)))
      val successor = assertNotNull(changed.featureTupleList.single()).tupleNumber
      val historyRequest = readRequest(collection).apply {
        queryHistory = true
        version = retained.version
      }
      val pending = assertSuccess(session.executeRead(historyRequest))
      assertHistorySuccessor(pending, retained, successor.version)
      session.commit()
      val committed = executeRead(historyRequest)
      assertHistorySuccessor(committed, retained, successor.version)
      // Keep the original write response and tuple strongly reachable through both reads.
      assertSame(retained, created.featureTupleList.single()?.tuple)
      assertEquals(Version.HEAD.number, retained.nextVersion)
    }
  }

  private fun assertHistorySuccessor(response: SuccessResponse, retained: Tuple, successor: Long) {
    val historic = assertNotNull(response.featureTupleList.single()?.tuple)
    assertEquals(retained.tupleNumber, historic.tupleNumber)
    assertEquals(successor, historic.nextVersion)
    val decoded = assertNotNull(response.features.single())
    assertEquals("created", decoded.title)
    assertEquals(successor, assertNotNull(decoded.properties.xyz.nguid).tupleNumber.version)
  }

  @Test
  fun repeatedHistoryReferencesStayLazyAndReuseFiniteCachedHistory() {
    useCollection("session_lazy_history")
    val created = insertFeature(feature("warm", "created"))
    val retained = assertNotNull(created.featureTupleList.single()?.tuple)
    val updated = executeWrite(request(Write().updateFeature(collection, feature("warm", "updated"), false)))
    val successor = assertNotNull(updated.featureTupleList.single()).tupleNumber.version
    val historicRequest = readRequest(collection).apply { queryHistory = true; version = retained.version }
    val session = object : PgSession(storage, newSessionOptions(), true) {
      var payloadLoads = 0
      override fun loadTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int) {
        payloadLoads++
        super.loadTuples(featureTuples, from, to)
      }
    }
    session.use {
      repeat(5) {
        val response = assertSuccess(session.executeRead(historicRequest))
        val historic = assertNotNull(response.featureTupleList.single()?.tuple)
        assertSame(retained.featureBytes, historic.featureBytes, "Refresh metadata, not payload bytes")
        assertEquals(successor, historic.nextVersion)
        assertEquals(Version.HEAD.number, retained.nextVersion, "Do not mutate a retained or globally cached HEAD")
      }
      assertEquals(0, session.payloadLoads, "Reference reads must never execute the SELECT t.* payload loader")

      // Load a finite historic tuple explicitly, then verify repeated references reuse that exact instance.
      val loaded = FeatureTupleList().apply { add(FeatureTuple(retained.tupleNumber, null)) }
      session.loadTuples(loaded, 0, loaded.size)
      val finite = assertNotNull(loaded.single()?.tuple)
      assertEquals(successor, finite.nextVersion)
      repeat(5) {
        assertSame(finite, assertSuccess(session.executeRead(historicRequest)).featureTupleList.single()?.tuple)
        val ranked = assertSuccess(session.executeRead(readRequest(collection).apply { queryHistory = true; versions = 2 }))
        assertSame(finite, ranked.featureTupleList.first { it?.tupleNumber == finite.tupleNumber }?.tuple)
      }
      assertEquals(1, session.payloadLoads)

      // Insert without populating the tuple cache: reference-only reads must leave a cache miss unloaded.
      session.useConnNonNull(true) { conn ->
        val pgCollection = assertNotNull(session.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue, conn))
        PgSession(storage, newSessionOptions(), false).use { writer ->
          val encoded = Tuple.encodeFeature(feature("cold", "cold"), pgCollection.naksha, writer)
          writer.useConnNonNull(false) { writeConn ->
            storage.database.rows_insert(writeConn, pgCollection.catalogId, pgCollection.id, listOf(encoded), pgCollection.storeHistory, pgCollection.columns)
          }
          writer.commit()
          assertNull(Naksha.cache[encoded.tuple.tupleNumber])
          repeat(5) {
            val response = assertSuccess(session.executeRead(readRequest(collection).apply { queryHistory = true }))
            val cold = assertNotNull(response.featureTupleList.first { it?.tupleNumber == encoded.tuple.tupleNumber })
            assertNull(cold.tuple, "A reference-only query must preserve lazy cache misses")
          }
        }
      }
      assertEquals(1, session.payloadLoads)
    }
  }

  @Test
  fun pendingHistoryMetadataDoesNotLeakAfterRollback() {
    useCollection("session_history_rollback")
    val created = insertFeature(feature("kept", "created"))
    val retained = assertNotNull(created.featureTupleList.single()?.tuple)
    storage.newWriteSession(newSessionOptions()).use { session ->
      val changed = assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("kept", "pending"), false))))
      val successor = assertNotNull(changed.featureTupleList.single()).tupleNumber.version
      val snapshot = readRequest(collection).apply { queryHistory = true; version = retained.version }
      assertHistorySuccessor(assertSuccess(session.executeRead(snapshot)), retained, successor)
      session.rollback()
      val response = executeRead(snapshot)
      assertSame(retained, response.featureTupleList.single()?.tuple)
      assertEquals(Version.HEAD.number, retained.nextVersion)
      assertNull(assertNotNull(response.features.single()).properties.xyz.nuuid)
    }
  }

  @Test
  fun coldHistoryReferenceRetainsPendingSuccessorThroughLazyLoad() = coldPendingHistory(false)

  @Test
  fun coldHistoryReferenceRetainsPendingDeleteThroughLazyLoad() = coldPendingHistory(true)

  private fun coldPendingHistory(delete: Boolean) {
    useCollection("session_cold_pending_$delete")
    // Insert through the SQL API so no tuple is published to the shared cache.
    val created = PgSession(storage, newSessionOptions(), false).use { writer ->
      val pgCollection = writer.useConnNonNull(false) { conn ->
        assertNotNull(writer.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue, conn))
      }
      val encoded = Tuple.encodeFeature(feature("cold", "created"), pgCollection.naksha, writer)
      writer.useConnNonNull(false) { conn ->
        storage.database.rows_insert(conn, pgCollection.catalogId, pgCollection.id, listOf(encoded), pgCollection.storeHistory, pgCollection.columns)
      }
      writer.commit()
      encoded.tuple
    }
    assertNull(Naksha.cache[created.tupleNumber])
    storage.newWriteSession(newSessionOptions()).use { session ->
      val mutation = if (delete) Write().deleteFeatureById(collection, "cold")
        else Write().updateFeature(collection, feature("cold", "pending"), false)
      val updated = assertSuccess(session.executeWrite(request(mutation)))
      val successor = assertNotNull(updated.featureTupleList.single()).tupleNumber.version
      val response = assertSuccess(session.executeRead(readRequest(collection).apply {
        queryHistory = true
        version = created.version
      }))
      val reference = assertNotNull(response.featureTupleList.single())
      assertNull(reference.tuple, "Reference query must not load a cold payload")
      val references = response.featureTupleList
      // The first cache load misses and opens a separate read session, which sees committed HEAD.
      Naksha.cache.load(references)
      assertEquals(successor, assertNotNull(reference.tuple).nextVersion, "Storage fallback must apply selected nv before feature decoding")
      val committedHead = assertNotNull(Naksha.cache[created.tupleNumber])
      assertEquals(Version.HEAD.number, committedHead.nextVersion, "Pending successor must remain response-local")
      reference.tuple = null
      Naksha.cache.load(references, loadFromStorage = false)
      assertEquals(successor, assertNotNull(reference.tuple).nextVersion, "Cache hit must apply selected nv before feature decoding")
      reference.tuple = null
      TupleHeapCache.getInstance().load(references)
      assertEquals(successor, assertNotNull(reference.tuple).nextVersion, "Direct heap-cache load must apply selected nv")
      reference.tuple = null
      storage.newReadSession(newSessionOptions()).use { reader -> reader.loadTuples(references) }
      assertEquals(successor, assertNotNull(reference.tuple).nextVersion, "Explicit session load must apply selected nv before feature decoding")
      reference.tuple = null
      references.loadAll()
      assertEquals(successor, assertNotNull(reference.tuple).nextVersion, "List lazy loading must apply selected nv")
      val decoded = assertNotNull(response.features.single())
      assertEquals("created", decoded.title)
      assertEquals(successor, assertNotNull(decoded.properties.xyz.nguid).tupleNumber.version)
      assertEquals(successor, assertNotNull(reference.tuple).nextVersion)
      assertEquals(Version.HEAD.number, committedHead.nextVersion, "Pending successor must remain response-local")
      session.rollback()
      assertEquals(Version.HEAD.number, committedHead.nextVersion)
    }
  }

  @Test
  fun successfulNoOpsDoNotReserveMutationIdentities() {
    useCollection("session_noop_identity")
    insertFeatures(feature("live", "seed"), feature("deleted", "seed"), feature("purged", "seed"))
    executeWrite(request(Write().deleteFeatureById(collection, "deleted"), Write().deleteFeatureById(collection, "purged")))
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(request(
        Write().deleteFeatureById(collection, "absent"),
        Write().purgeFeatureById(collection, "live"),
        Write().purgeFeatureById(collection, "absent_purge"),
        Write().deleteFeatureById(collection, "deleted"))))
      assertSuccess(session.executeWrite(request(
        Write().createFeature(collection, feature("absent", "created")),
        Write().updateFeature(collection, feature("live", "updated"), false),
        Write().createFeature(collection, feature("absent_purge", "created")),
        Write().createFeature(collection, feature("deleted", "recreated")))))
      assertSuccess(session.executeWrite(request(Write().purgeFeatureById(collection, "purged"))))
      assertTrue(assertIs<ErrorResponse>(session.executeWrite(request(
        Write().createFeature(collection, feature("purged", "forbidden"))))).error.isConflict(),
        "A real purge must reserve its identity even though it has no response tuple")
      session.commit()
    }
    assertEquals(mapOf("absent" to "created", "live" to "updated", "absent_purge" to "created", "deleted" to "recreated"),
      titles(executeRead(readRequest(collection))))
  }

  @Test
  fun atomicIdempotentDeleteDoesNotReserveIdentity() {
    useCollection("session_atomic_noop_identity")
    val created = insertFeature(feature("deleted", "seed"))
    val live = assertNotNull(created.features.single())
    executeWrite(request(Write().deleteFeatureById(collection, "deleted")))
    storage.newWriteSession(newSessionOptions()).use { session ->
      // An already-deleted HEAD is accepted even with its former live UUID, without creating a new state.
      assertSuccess(session.executeWrite(request(Write().deleteFeature(collection, live, true))))
      assertSuccess(session.executeWrite(request(Write().createFeature(collection, feature("deleted", "recreated")))))
      session.commit()
    }
    assertEquals(mapOf("deleted" to "recreated"), titles(executeRead(readRequest(collection))))
  }

  @Test
  fun failedPurgeRequestReservesNothingWithAndWithoutHistory() {
    for (history in listOf(false, true)) {
      useCollection(NakshaCollection("session_purge_retry_$history", catalog.id).apply {
        storeHistory = if (history) StoreMode.ON else StoreMode.OFF
      })
      insertFeatures(feature("purged", "seed"), feature("live", "seed"))
      executeWrite(request(Write().deleteFeatureById(collection, "purged")))
      storage.newWriteSession(newSessionOptions()).use { session ->
        assertSuccess(session.executeWrite(request(Write().createFeature(collection, feature("prior", "kept")))))
        assertIs<ErrorResponse>(session.executeWrite(request(
          Write().purgeFeatureById(collection, "purged"),
          Write().updateFeature(collection, feature("missing", "invalid"), false))))
        assertSuccess(session.executeWrite(request(
          Write().purgeFeatureById(collection, "purged"),
          Write().purgeFeatureById(collection, "live"))))
        assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("live", "updated"), false))))
        assertTrue(assertIs<ErrorResponse>(session.executeWrite(request(
          Write().createFeature(collection, feature("purged", "forbidden"))))).error.isConflict())
        session.commit()
      }
      assertEquals(mapOf("prior" to "kept", "live" to "updated"), titles(executeRead(readRequest(collection))))
    }
  }

  @Test
  fun historyReferenceOverheadMeasurement() {
    useCollection("session_reference_overhead")
    val count = 512
    val writes = WriteRequest()
    for (i in 0 until count) writes.add(Write().createFeature(collection, feature("bench-$i", "x".repeat(4096))))
    val retained = executeWrite(writes)
    val historicRequest = readRequest(collection).apply { queryHistory = true }
    PgSession(storage, newSessionOptions(), false).use { session ->
      // Hold one connection and warm metadata, plans and cache before timing.
      session.useConnNonNull(false) { }
      val lazy = ArrayList<Long>()
      val eager = ArrayList<Long>()
      repeat(25) { run ->
        for (reload in if (run % 2 == 0) listOf(false, true) else listOf(true, false)) {
          val start = System.nanoTime()
          val response = assertSuccess(session.executeRead(historicRequest))
          val references = response.featureTupleList
          assertEquals(count, references.size)
          if (reload) {
            for (reference in references) reference?.tuple = null
            session.loadTuples(references, 0, references.size)
          }
          val nanos = System.nanoTime() - start
          if (run >= 5) (if (reload) eager else lazy).add(nanos)
        }
      }
      lazy.sort()
      eager.sort()
      println("SESSION_REFERENCE_BENCH rows=$count title_chars_per_row=4096 samples=20 lazy_median_ms=${lazy[10] / 1e6} eager_median_ms=${eager[10] / 1e6} extra_reference_bigints_per_row=1 payload_selects_lazy=0 payload_selects_eager=1")
      val query = PgQueryBuilder(session, historicRequest).build()
      session.useConnNonNull(true) { conn ->
        val baseline = ArrayList<Long>()
        val projected = ArrayList<Long>()
        val withoutNv = query.sql.replace("\nSELECT fn, version, nv FROM ", "\nSELECT fn, version FROM ")
        assertNotEquals(query.sql, withoutNv)
        repeat(45) { run ->
          for (includeNv in if (run % 2 == 0) listOf(false, true) else listOf(true, false)) {
            val start = System.nanoTime()
            conn.prepare(if (includeNv) query.sql else withoutNv, query.argTypes).use { plan ->
              plan.execute(query.argValues).use { cursor ->
                var rows = 0
                while (cursor.next()) {
                  cursor.get<Long>("fn")
                  cursor.get<Long>("version")
                  if (includeNv) cursor.get<Long>("nv")
                  rows++
                }
                assertEquals(count, rows)
              }
            }
            if (run >= 5) (if (includeNv) projected else baseline).add(System.nanoTime() - start)
          }
        }
        baseline.sort()
        projected.sort()
        println("SESSION_NV_PROJECTION_BENCH rows=$count samples=40 baseline_median_ms=${baseline[20] / 1e6} nv_median_ms=${projected[20] / 1e6}")
      }
      assertEquals(count, retained.featureTupleList.size)
    }
  }

  @Test
  fun twoUpdatesInOneVersionRejectEntireSecondRequest() {
    useCollection("session_two_updates")
    insertFeatures(feature("first", "seed"), feature("other", "seed"))
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("first", "once"), false))))
      val version = assertNotNull(session.getTransaction()).version
      val rejected = session.executeWrite(request(
        Write().updateFeature(collection, feature("other", "must_not_write"), false),
        Write().updateFeature(collection, feature("first", "twice"), false)))
      assertTrue(assertIs<ErrorResponse>(rejected).error.isConflict())
      assertEquals(version, session.getTransaction()?.version)
      assertEquals(mapOf("first" to "once", "other" to "seed"), titles(assertSuccess(session.executeRead(readRequest(collection)))))
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("other", "retry"), false))))
      session.commit()
    }
    assertEquals(mapOf("first" to "once", "other" to "retry"), titles(executeRead(readRequest(collection))))
  }

  @Test
  fun duplicateBatchRejectsBeforeAnyWriteAndReservesNothing() {
    useCollection("session_duplicate_batch")
    storage.newWriteSession(newSessionOptions()).use { session ->
      val rejected = session.executeWrite(request(
        Write().createFeature(collection, feature("other", "unwritten")),
        Write().createFeature(collection, feature("duplicate", "first")),
        Write().upsertFeature(collection, feature("duplicate", "second"), false)))
      assertTrue(assertIs<ErrorResponse>(rejected).error.isConflict())
      assertNull(session.getTransaction(), "Duplicate preflight must not start a transaction")
      assertTrue(assertSuccess(session.executeRead(readRequest(collection))).features.isEmpty())
      assertSuccess(session.executeWrite(request(
        Write().createFeature(collection, feature("other", "retry")),
        Write().createFeature(collection, feature("duplicate", "retry")))))
      session.commit()
    }
    assertEquals(mapOf("other" to "retry", "duplicate" to "retry"), titles(executeRead(readRequest(collection))))
  }

  @Test
  fun sameIdInDifferentCollectionsAndCatalogsIsIndependent() {
    val first = initCollection(catalog.id, "session_identity_a").second
    val second = initCollection(catalog.id, "session_identity_b").second
    val foreign = initCollection("session_identity_catalog", "session_identity_a").second
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(request(Write().createFeature(first, feature("shared", "first")))))
      assertSuccess(session.executeWrite(request(
        Write().createFeature(second, feature("shared", "second")),
        Write().createFeature(foreign, feature("shared", "foreign")))))
      session.commit()
    }
    for ((target, title) in listOf(first to "first", second to "second", foreign to "foreign")) {
      assertEquals(mapOf("shared" to title), titles(executeRead(readRequest(target))))
    }
  }

  @Test
  fun failedRequestRetryPreservesEarlierReservations() {
    useCollection("session_failed_retry")
    insertFeatures(feature("existing", "seed"), feature("retry", "seed"))
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("existing", "prior"), false))))
      val version = assertNotNull(session.getTransaction()).version
      assertIs<ErrorResponse>(session.executeWrite(request(
        Write().updateFeature(collection, feature("retry", "rolled_back"), false),
        Write().updateFeature(collection, feature("missing", "invalid"), false))))
      assertEquals(mapOf("existing" to "prior", "retry" to "seed"), titles(assertSuccess(session.executeRead(readRequest(collection)))))
      assertTrue(assertIs<ErrorResponse>(session.executeWrite(request(
        Write().updateFeature(collection, feature("existing", "forbidden"), false)))).error.isConflict())
      assertSuccess(session.executeWrite(request(
        Write().updateFeature(collection, feature("retry", "retried"), false),
        Write().createFeature(collection, feature("missing", "retried")))))
      assertEquals(version, session.getTransaction()?.version)
      session.commit()
    }
    assertEquals(mapOf("existing" to "prior", "retry" to "retried", "missing" to "retried"), titles(executeRead(readRequest(collection))))
  }

  @Test
  fun failedFirstRequestCanRetryAndCommitAndRollbackResetTracking() {
    useCollection("session_reset_tracking")
    insertFeature(feature("kept", "seed"))
    storage.newWriteSession(newSessionOptions()).use { session ->
      assertIs<ErrorResponse>(session.executeWrite(request(
        Write().updateFeature(collection, feature("kept", "rolled_back"), false),
        Write().updateFeature(collection, feature("missing", "invalid"), false))))
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("kept", "committed"), false))))
      val firstVersion = assertNotNull(session.getTransaction()).version
      session.commit()
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("kept", "aborted"), false))))
      assertNotEquals(firstVersion, session.getTransaction()?.version)
      session.rollback()
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("kept", "after_rollback"), false))))
      session.commit()
      assertSuccess(session.executeWrite(request(Write().updateFeature(collection, feature("kept", "after_commit"), false))))
      session.commit()
    }
    assertEquals(mapOf("kept" to "after_commit"), titles(executeRead(readRequest(collection))))
  }

  private fun feature(id: String, title: String) = NakshaFeature(id).apply { this.title = title }

  private fun request(vararg writes: Write) = WriteRequest().apply {
    for (write in writes) add(write)
  }

  private fun readRequest(target: NakshaCollection) = ReadFeatures().apply {
    catalogId = target.catalogId
    collectionId = target.id
  }

  private fun titles(response: SuccessResponse): Map<String, String?> {
    val result = HashMap<String, String?>()
    for (feature in response.features) {
      val present = assertNotNull(feature)
      result[present.id] = present.title
    }
    return result
  }
}
