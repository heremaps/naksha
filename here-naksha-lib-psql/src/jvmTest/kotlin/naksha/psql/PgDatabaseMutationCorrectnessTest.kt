package naksha.psql

import java.sql.DriverManager
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import naksha.base.Action
import naksha.base.Guid
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.jbon.HeapBook
import naksha.model.Naksha
import naksha.model.NewTuple
import naksha.model.Tuple
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_MUTATION_DB_URL", matches = ".+")
class PgDatabaseMutationCorrectnessTest {
  @Test fun createAgainstLiveHeadAlwaysReportsFeatureExists() = fixture { db, conn, col, version ->
    for (id in listOf("hashed-existing", "83")) {
      val seed = encode(col, id, version + 8)
      insert(db, conn, col, listOf(seed))
      for (liveAction in listOf(Action.CREATE, Action.UPDATE)) {
        val actual = if (liveAction == Action.CREATE) seed.tuple else {
          val updated = encode(col, id, version + 12, Action.UPDATE)
          db.rows_update(conn, col.catalogId, col.id, listOf(updated), col.storeHistory, col.columns).single().tuple
        }
        for (incomingVersion in listOf(version + 4, actual.version and -4L, version + 16)) {
          val incoming = encode(col, id, incomingVersion)
          val frozen = freeze(listOf(incoming))
          val before = oracle(conn, col)
          val error = assertFailsWith<NakshaException> { insert(db, conn, col, listOf(incoming)) }.error
          assertEquals(NakshaError.FEATURE_EXISTS, error.code)
          assertEquals(actual.version, error.actualVersion)
          assertNull(error.expectedVersion)
          assertEquals(id, error.id)
          assertEquals(db.id.text, error.database)
          assertEquals(col.catalogId.text, error.catalog)
          assertEquals(col.id.text, error.collection)
          assertEquals(before, oracle(conn, col))
          assertEquals(frozen, freeze(listOf(incoming)))
        }
      }
    }
  }

  @Test fun overlappingUpdateReadsReplacementHead() = overlappingMutation("update")
  @Test fun overlappingDeleteReadsReplacementHead() = overlappingMutation("delete")
  @Test fun overlappingPurgeReadsReplacementHead() = overlappingMutation("purge")

  private fun overlappingMutation(op: String) = fixture(committed = true) { db, conn, col, version ->
    val executor = Executors.newSingleThreadExecutor()
    try {
      for (commit in listOf(false, true)) {
        for (id in listOf("overlap_${op}_$commit", if (commit) "81" else "82")) {
          val seed = encode(col, id, version)
          insert(db, conn, col, listOf(seed))
          conn.commit()
          DriverManager.getConnection(System.getenv("NAKSHA_TEST_MUTATION_DB_URL")).use { jdbc ->
            val other = PgConnection(9878L, conn.instance, conn.options, jdbc as org.postgresql.jdbc.PgConnection)
            other.autoCommit = false
            other.execute("SET LOCAL lock_timeout = '10s'; SET LOCAL statement_timeout = '15s'").close()
            val pid = strings(other, "SELECT pg_backend_pid()::text AS value").single()
            // PURGE of a live HEAD is a no-op, but must take the same stable feature lock as every mutation.
            assertEquals(seed.tuple.tupleNumber, db.rows_purge(conn, col, version + 4, listOf(Id(id))).single()?.tupleNumber)
            val ordinal = Id.featureNumberToOrdinal(Id(id).number)
            conn.execute("SELECT fn FROM ${col.qualifiedName} WHERE fn = $ordinal AND nv = ${Version.HEAD.number} FOR UPDATE").close()
            val incoming = encode(col, id, version + 8, Action.UPDATE)
            val frozen = freeze(listOf(incoming))
            val future = executor.submit(Callable {
              when (op) {
                "update" -> db.rows_update(other, col.catalogId, col.id, listOf(incoming), col.storeHistory, col.columns).single()
                "delete" -> db.rows_delete_by_id_with_result(other, col, version + 8, listOf(Id(id))).single()
                else -> db.rows_purge(other, col, version + 8, listOf(Id(id))).single()
              }
            })
            try {
              val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
              var waiting = false
              while (System.nanoTime() < deadline) {
                waiting = strings(conn, "SELECT count(*)::text AS value FROM pg_stat_activity WHERE pid = $pid AND wait_event_type = 'Lock' AND wait_event = 'advisory'").single() == "1"
                if (waiting) break
                Thread.sleep(10)
              }
              assertTrue(waiting, "The contender must wait on the feature lock before reading HEAD")
              val replacement = if (op == "purge") {
                db.rows_delete_by_id_with_result(conn, col, version + 4, listOf(Id(id))).single().tuple
              } else {
                val next = encode(col, id, version + 4, Action.UPDATE)
                db.rows_update(conn, col.catalogId, col.id, listOf(next), col.storeHistory, col.columns).single().tuple
              }
              if (commit) conn.commit() else conn.rollback()
              val expected = if (commit) replacement else seed.tuple
              val result = future.get(15, TimeUnit.SECONDS)
              if (op == "purge") {
                assertEquals(expected.tupleNumber, (result as Guid).tupleNumber)
                val head = strings(other, "SELECT version::text AS value FROM ${col.qualifiedName} WHERE fn = $ordinal AND nv = ${Version.HEAD.number}")
                assertEquals(if (commit) emptyList() else listOf(seed.tuple.version.toString()), head)
                if (commit && col.storeHistory) assertState(other, col, replacement, version + 8)
              } else {
                val saved = result as NewTuple
                assertEquals(expected.tupleNumber, saved.previousTupleNumber)
                assertEquals(if (op == "update") Action.UPDATE else Action.DELETE, saved.tuple.action)
                assertState(other, col, saved.tuple, Version.HEAD.number)
                if (col.storeHistory) assertState(other, col, expected, saved.tuple.version)
              }
              assertEquals(frozen, freeze(listOf(incoming)))
              assertEquals(listOf("1"), strings(other, "SELECT 1::text AS value"))
            } finally {
              conn.rollback()
              try { future.get(15, TimeUnit.SECONDS) } finally { other.rollback() }
            }
          }
        }
      }
    } finally { executor.shutdownNow() }
  }

  @Test fun newerSuccessorStillReportsChangeCountOverflow() = fixture(changeCount = true) { db, conn, col, version ->
    val seed = encode(col, "overflow", version)
    insert(db, conn, col, listOf(seed))
    val cc = assertNotNull(col.column(StandardMembers.ChangeCount)).ident
    conn.execute("UPDATE ${col.qualifiedName} SET $cc = ${Int.MAX_VALUE}").close()
    val before = oracle(conn, col)
    val incoming = NewTuple(encode(col, "overflow", version + 4).tuple, seed.tuple.tupleNumber)
    val frozen = freeze(listOf(incoming))
    val error = assertFailsWith<NakshaException> { upsert(db, conn, col, listOf(incoming)) }.error
    assertEquals(NakshaError.ILLEGAL_STATE, error.code)
    assertEquals(seed.tuple.version, error.actualVersion)
    assertEquals(seed.tuple.version, error.expectedVersion)
    assertEquals(before, oracle(conn, col))
    assertEquals(frozen, freeze(listOf(incoming)))
  }

  @Test fun purgeMovesExactTombstoneThroughRootBeyondInitialCoverage() = fixture(partitions = 7) { db, conn, col, version ->
    for (id in listOf("hashed-purge-route", "47")) {
      insert(db, conn, col, listOf(encode(col, id, version)))
      val deleted = db.rows_delete_by_id_with_result(conn, col, version + 4, listOf(Id(id))).single().tuple
      val purgeVersion = ((version shr 12) + 32) shl 12
      if (col.storeHistory) db.collection_maintain_partitions(conn, col.catalogId.catalogName(), col.name, purgeVersion)
      val before = oracle(conn, col)
      val savepoint = conn.jdbc.setSavepoint()
      assertEquals(deleted.tupleNumber, db.rows_purge(conn, col, purgeVersion, listOf(Id(id))).single()?.tupleNumber)
      assertNull(db.rows_purge(conn, col, purgeVersion + 4, listOf(Id(id))).single())
      if (col.storeHistory) {
        assertState(conn, col, deleted, purgeVersion)
        val leaf = strings(conn, "SELECT tableoid::regclass::text AS value FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(Id(id).number)} AND version = ${deleted.version} AND nv = $purgeVersion").single()
        assertTrue(leaf.contains("${'$'}${purgeVersion shr 12}${'$'}"), leaf)
      } else assertTrue(strings(conn, "SELECT version::text AS value FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(Id(id).number)}").isEmpty())
      conn.jdbc.rollback(savepoint)
      conn.jdbc.releaseSavepoint(savepoint)
      assertEquals(before, oracle(conn, col))
    }
  }

  @Test fun mutationsRejectOlderAndSameTransactionSuccessors() = fixture { db, conn, col, version ->
    val seed = encode(col, "monotonic", version + 16)
    insert(db, conn, col, listOf(seed))
    for (incomingVersion in listOf(version + 4, version + 16)) {
      for (op in listOf("update", "upsert", "explicit", "delete")) {
        val action = when (op) { "update" -> Action.UPDATE; "explicit" -> Action.DELETE; else -> Action.CREATE }
        val incoming = encode(col, "monotonic", incomingVersion, action)
        val frozen = freeze(listOf(incoming))
        val before = oracle(conn, col)
        val savepoint = conn.jdbc.setSavepoint()
        try {
          val error = assertFailsWith<NakshaException>("$op history=${col.storeHistory}") {
            when (op) {
              "update" -> db.rows_update(conn, col.catalogId, col.id, listOf(incoming), col.storeHistory, col.columns)
              "upsert" -> upsert(db, conn, col, listOf(incoming))
              "explicit" -> db.rows_delete_with_new_state(conn, col, incomingVersion, listOf(incoming))
              else -> db.rows_delete_by_id_with_result(conn, col, incomingVersion, listOf(seed.tuple.id))
            }
          }.error
          assertEquals(NakshaError.CONFLICT, error.code)
          assertEquals(seed.tuple.version, error.actualVersion)
          assertEquals("monotonic", error.id)
          assertEquals(db.id.text, error.database)
          assertEquals(col.catalogId.text, error.catalog)
          assertEquals(col.id.text, error.collection)
          assertEquals(before, oracle(conn, col))
          assertEquals(frozen, freeze(listOf(incoming)))
        } finally { conn.jdbc.rollback(savepoint); conn.jdbc.releaseSavepoint(savepoint) }
      }
    }
    val deleted = db.rows_delete_by_id_with_result(conn, col, version + 20, listOf(seed.tuple.id)).single()
    for (op in listOf("insert", "upsert", "purge", "explicit")) {
      val incoming = encode(col, "monotonic", version + 8, if (op == "explicit") Action.DELETE else Action.CREATE)
      val before = oracle(conn, col)
      val savepoint = conn.jdbc.setSavepoint()
      try {
        val error = assertFailsWith<NakshaException> {
          when (op) {
            "insert" -> insert(db, conn, col, listOf(incoming))
            "upsert" -> upsert(db, conn, col, listOf(incoming))
            "explicit" -> db.rows_delete_with_new_state(conn, col, version + 8,
              listOf(NewTuple(incoming.tuple, deleted.tuple.tupleNumber)))
            else -> db.rows_purge(conn, col, version + 8, listOf(seed.tuple.id))
          }
        }.error
        assertEquals(NakshaError.CONFLICT, error.code, op)
        assertEquals(deleted.tuple.version, error.actualVersion)
        assertEquals(before, oracle(conn, col))
      } finally { conn.jdbc.rollback(savepoint); conn.jdbc.releaseSavepoint(savepoint) }
    }
  }

  @Test fun concurrentAbsentUpsertReclassifiesWithoutPoisoningTransaction() = fixture(committed = true) { db, conn, col, version ->
    val executor = Executors.newSingleThreadExecutor()
    try {
      for (outcome in listOf("commit", "rollback", "older", "atomic", "deleted")) {
        DriverManager.getConnection(System.getenv("NAKSHA_TEST_MUTATION_DB_URL")).use { jdbc ->
          val other = PgConnection(9876L, conn.instance, conn.options, jdbc as org.postgresql.jdbc.PgConnection)
          other.autoCommit = false
          other.execute("SET LOCAL lock_timeout = '10s'; SET LOCAL statement_timeout = '15s'").close()
          val pid = strings(other, "SELECT pg_backend_pid()::text AS value").single()
          val id = "race_$outcome"
          val winner = encode(col, id, version + 8)
          insert(db, conn, col, listOf(winner))
          val winnerState = if (outcome == "deleted")
            db.rows_delete_by_id_with_result(conn, col, version + 12, listOf(Id(id))).single().tuple else winner.tuple
          val incoming = encode(col, id, if (outcome == "older") version + 4 else version + 16)
          val input = NewTuple(incoming.tuple, if (outcome == "atomic") TupleNumber.copy(winner.tuple.tupleNumber, version = version) else null)
          val frozen = freeze(listOf(input))
          val future = executor.submit(Callable {
            try { upsert(db, other, col, listOf(input)).single() } catch (e: NakshaException) { e }
          })
          try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            var waiting = false
            while (System.nanoTime() < deadline) {
              waiting = strings(conn, "SELECT count(*)::text AS value FROM pg_stat_activity WHERE pid = $pid AND wait_event_type = 'Lock' AND wait_event = 'advisory'").single() == "1"
              if (waiting) break
              Thread.sleep(10)
            }
            assertTrue(waiting, "Contender must wait on the uncommitted inserter's feature lock before reading HEAD")
            if (outcome == "rollback") conn.rollback() else conn.commit()
            val result = future.get(15, TimeUnit.SECONDS)
            if (outcome == "older" || outcome == "atomic") {
              val error = (result as NakshaException).error
              assertEquals(NakshaError.CONFLICT, error.code)
              assertEquals(winnerState.version, error.actualVersion)
              assertEquals(input.previousTupleNumber?.version, error.expectedVersion)
              assertState(other, col, winnerState, Version.HEAD.number)
            } else {
              val saved = result as NewTuple
              assertEquals(if (outcome == "commit") winnerState.tupleNumber else null, saved.previousTupleNumber)
              assertEquals(if (outcome == "commit") Action.UPDATE else Action.CREATE, saved.tuple.action)
              assertState(other, col, saved.tuple, Version.HEAD.number)
              if (col.storeHistory && outcome != "rollback") assertState(other, col, winnerState, saved.tuple.version)
            }
            assertEquals(frozen, freeze(listOf(input)))
            assertEquals(listOf("1"), strings(other, "SELECT 1::text AS value"), "No unique violation may poison the transaction")
          } finally {
            conn.rollback()
            future.get(15, TimeUnit.SECONDS)
            other.rollback()
          }
        }
      }
    } finally { executor.shutdownNow() }
  }

  @Test fun olderBackendCannotOverwriteNewerCommittedHead() = fixture(committed = true) { db, conn, col, version ->
    DriverManager.getConnection(System.getenv("NAKSHA_TEST_MUTATION_DB_URL")).use { jdbc ->
      val older = PgConnection(9877L, conn.instance, conn.options, jdbc as org.postgresql.jdbc.PgConnection)
      older.autoCommit = false
      val incoming = encode(col, "older_backend", version + 4, Action.UPDATE)
      val frozen = freeze(listOf(incoming))
      strings(older, "SELECT pg_backend_pid()::text AS value")
      val newer = encode(col, "older_backend", version + 8)
      insert(db, conn, col, listOf(newer))
      conn.commit()
      try {
        val error = assertFailsWith<NakshaException> {
          db.rows_update(older, col.catalogId, col.id, listOf(incoming), col.storeHistory, col.columns)
        }.error
        assertEquals(NakshaError.CONFLICT, error.code)
        assertEquals(newer.tuple.version, error.actualVersion)
        assertState(older, col, newer.tuple, Version.HEAD.number)
        assertEquals(frozen, freeze(listOf(incoming)))
      } finally { older.rollback() }
    }
  }

  @Test fun explicitDeleteRejectsWrongAction() = rejectsExplicit("action")
  @Test fun explicitDeleteRejectsForeignDatabase() = rejectsExplicit("database")
  @Test fun explicitDeleteRejectsForeignCatalog() = rejectsExplicit("catalog")
  @Test fun explicitDeleteRejectsForeignCollection() = rejectsExplicit("collection")
  @Test fun explicitDeleteRejectsNonHead() = rejectsExplicit("nv")
  @Test fun explicitDeleteRejectsInfiniteVersion() = rejectsExplicit("version")
  @Test fun explicitDeleteRejectsMissingPayload() = rejectsExplicit("payload")
  @Test fun explicitDeleteRejectsForeignPredecessor() = rejectsExplicit("predecessor")
  @Test fun explicitDeleteRejectsNegativeVersion() = rejectsExplicit("negative")
  @Test fun explicitDeleteRejectsOtherFeaturePredecessor() = rejectsExplicit("feature")

  private fun rejectsExplicit(kind: String) = fixture { db, conn, col, version ->
    val seeds = listOf(encode(col, "first", version), encode(col, "second", version))
    insert(db, conn, col, seeds)
    val before = oracle(conn, col)
    val first = encode(col, "first", version + 4, Action.DELETE)
    val original = encode(col, "second", version + 4, Action.DELETE).tuple
    val tn = original.tupleNumber
    val book = HeapBook.copyOf(original.membersBook)
    when (kind) {
      "action" -> book.put(StandardMembers.TN, TupleNumber.copy(tn, version = version + 4))
      "database" -> book.put(StandardMembers.TN, TupleNumber(tn.databaseNumber + 1, tn.catalogNumber, tn.collectionNumber, tn.featureNumber, tn.version))
      "catalog" -> book.put(StandardMembers.TN, TupleNumber(tn.databaseNumber, tn.catalogNumber + 1, tn.collectionNumber, tn.featureNumber, tn.version))
      "collection" -> book.put(StandardMembers.TN, TupleNumber.copy(tn, collectionNumber = tn.collectionNumber + 1))
      "nv" -> book.put(StandardMembers.NextVersion.name, version + 8)
      "version" -> book.put(StandardMembers.TN, TupleNumber.copy(tn, version = Version.HEAD.number or 2L))
      "negative" -> book.put(StandardMembers.TN, TupleNumber.copy(tn, version = -2L))
    }
    val bad = NewTuple(Tuple(if (kind == "payload") byteArrayOf() else original.featureBytes, book),
      when (kind) {
        "predecessor" -> TupleNumber(tn.databaseNumber + 1, tn.catalogNumber, tn.collectionNumber, tn.featureNumber, seeds[1].tuple.version)
        "feature" -> seeds[0].tuple.tupleNumber
        else -> null
      })
    val inputs = listOf(first, bad)
    val frozen = freeze(inputs)
    val error = assertFailsWith<NakshaException> { db.rows_delete_with_new_state(conn, col, version + 4, inputs) }.error
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.code, kind)
    assertEquals(db.id.text, error.database)
    assertEquals(col.catalogId.text, error.catalog)
    assertEquals(col.id.text, error.collection)
    assertEquals("second", error.id)
    assertEquals(before, oracle(conn, col), "Full batch validation must precede mutation: $kind")
    assertEquals(frozen, freeze(inputs))
  }

  @Test fun upsertRejectsNonCreateBeforeMutatingBatch() = fixture { db, conn, col, version ->
    for (action in listOf(Action.UPDATE, Action.DELETE)) {
      val inputs = listOf(encode(col, "first", version), encode(col, "second", version, action))
      val before = freeze(inputs)
      val error = assertFailsWith<NakshaException> { upsert(db, conn, col, inputs) }.error
      assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.code)
      assertTrue(oracle(conn, col).isEmpty())
      assertEquals(before, freeze(inputs))
    }
  }

  @Test fun emptyTombstonesContainOnlyKeys() = fixture { db, conn, col, version ->
    for (id in listOf("hashed-empty", "42")) {
      val seed = encode(col, id, version)
      insert(db, conn, col, listOf(seed))
      val deleted = db.rows_delete_by_id_without_result(conn, col, version + 4, listOf(Id(id)), true).single()
      assertEquals(id, deleted.id)
      val keys = setOf("fn", "version", "nv")
      conn.execute("SELECT * FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(Id(id).number)} AND nv = ${Version.HEAD.number}").use { cursor ->
        assertTrue(cursor.next())
        for (column in col.columns) if (column.columnName !in keys) assertNull(cursor.column(column.columnName), column.name)
      }
    }
  }

  @Test fun emptyTombstonesCanBeDeletedAgainAndPurged() = fixture { db, conn, col, version ->
    for (id in listOf("hashed-repeat", "43")) {
      insert(db, conn, col, listOf(encode(col, id, version)))
      val deleted = db.rows_delete_by_id_without_result(conn, col, version + 4, listOf(Id(id)), true).single()
      val before = oracle(conn, col)
      val again = db.rows_delete_by_id_with_result(conn, col, version + 8, listOf(Id(id))).single()
      assertEquals(deleted.tupleNumber, again.tuple.tupleNumber)
      assertEquals(id, again.tuple.id.text)
      assertNull(again.previousTupleNumber)
      assertEquals(deleted, db.rows_delete_by_guid_without_result(conn, col, version + 8,
        listOf(Guid(id, TupleNumber.copy(deleted.tupleNumber, version = version))), true).single())
      assertEquals(before, oracle(conn, col))
      assertEquals(deleted, db.rows_purge(conn, col, version + 8, listOf(Id(id))).single())
      assertEquals(if (col.storeHistory) 2 else 0, strings(conn,
        "SELECT version::text AS value FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(Id(id).number)}").size)
      if (col.storeHistory) assertState(conn, col, encode(col, id, version).tuple, deleted.tupleNumber.version)
      assertNull(db.rows_purge(conn, col, version + 12, listOf(Id(id))).single())
    }
  }

  @Test fun emptyTombstonesCanBeRecreatedByInsertAndUpsert() = fixture { db, conn, col, version ->
    for (useUpsert in listOf(false, true)) {
      for (id in listOf("hashed-recreate-$useUpsert", if (useUpsert) "45" else "46")) {
        insert(db, conn, col, listOf(encode(col, id, version)))
        db.rows_delete_by_id_without_result(conn, col, version + 4, listOf(Id(id)), true)
        val incoming = encode(col, id, version + 8)
        val before = freeze(listOf(incoming))
        val result = if (useUpsert) {
          val result = upsert(db, conn, col, listOf(incoming)).single()
          assertNull(result.previousTupleNumber)
          result.tuple
        } else insert(db, conn, col, listOf(incoming)).single()
        assertEquals(incoming.tuple.tupleNumber, result.tupleNumber)
        assertState(conn, col, result, Version.HEAD.number)
        assertEquals(before, freeze(listOf(incoming)))
        if (col.storeHistory) {
          val rows = PgRows().withCollection(col)
          conn.execute("SELECT * FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(Id(id).number)} AND nv = ${result.version}")
            .fetch().use { rows.readAll(it) }
          val empty = assertNotNull(rows.getTuple(0, id))
          assertEquals(Action.DELETE, empty.action)
          assertTrue(empty.featureBytes.isEmpty())
          assertNull(empty.membersBook["name"])
          assertEquals(id, empty.id.text)
          val transport = PgRows().withCollection(col).apply { size = 1; setRow(0, empty) }
          assertNull(transport.getAny(0, StandardMembers.FeatureBytes.name))
          assertNull(transport.getAny(0, StandardMembers.Id.name))
        }
      }
    }
  }

  @Test fun explicitDeleteUsesExactPredecessorsAndCallerRollsBackLateConflict() = fixture { db, conn, col, version ->
    val seeds = listOf(encode(col, "first", version), encode(col, "second", version))
    insert(db, conn, col, seeds)
    val before = oracle(conn, col)
    val inputs = listOf(encode(col, "first", version + 4, Action.DELETE),
      NewTuple(encode(col, "second", version + 4, Action.DELETE).tuple,
        TupleNumber.copy(seeds[1].tuple.tupleNumber, version = seeds[1].tuple.version or 1L)))
    val frozen = freeze(inputs)
    val savepoint = conn.jdbc.setSavepoint()
    val error = assertFailsWith<NakshaException> { db.rows_delete_with_new_state(conn, col, version + 4, inputs) }.error
    assertEquals(NakshaError.CONFLICT, error.code)
    assertEquals(inputs[1].previousTupleNumber?.version, error.expectedVersion)
    assertEquals(seeds[1].tuple.version, error.actualVersion)
    assertState(conn, col, inputs[0].tuple, Version.HEAD.number)
    conn.jdbc.rollback(savepoint)
    conn.jdbc.releaseSavepoint(savepoint)
    assertEquals(before, oracle(conn, col))
    assertEquals(frozen, freeze(inputs))
    val deleted = db.rows_delete_with_new_state(conn, col, version + 4, listOf(inputs[0])).single()
    val next = encode(col, "first", version + 8, Action.DELETE)
    assertEquals(NakshaError.CONFLICT, assertFailsWith<NakshaException> {
      db.rows_delete_with_new_state(conn, col, version + 8, listOf(next))
    }.error.code)
    val replaced = db.rows_delete_with_new_state(conn, col, version + 8,
      listOf(NewTuple(next.tuple, deleted.tuple.tupleNumber))).single()
    assertEquals(deleted.tuple.tupleNumber, replaced.previousTupleNumber)
    assertState(conn, col, next.tuple, Version.HEAD.number)
  }

  @Test fun nullPayloadOnLiveHeadStillFailsDecoding() = fixture { db, conn, col, version ->
    val incoming = encode(col, "hashed-live", version)
    insert(db, conn, col, listOf(incoming))
    val blob = assertNotNull(col.column(StandardMembers.FeatureBytes)).ident
    conn.execute("UPDATE ${col.qualifiedName} SET $blob = NULL").close()
    val rows = PgRows().withCollection(col)
    conn.execute("SELECT * FROM ${col.qualifiedName}").fetch().use { rows.readAll(it) }
    assertEquals(NakshaError.ILLEGAL_STATE, assertFailsWith<NakshaException> { rows.getTuple(0, "hashed-live") }.error.code)
  }

  @Test fun lifecycleMatchesSqlOracleAndCallerRollback() = fixture { db, conn, col, version ->
    for (id in listOf("hashed-lifecycle", "44")) {
      val create = encode(col, id, version)
      val frozen = freeze(listOf(create))
      val inserted = upsert(db, conn, col, listOf(create)).single()
      assertNull(inserted.previousTupleNumber)
      assertEquals(create.tuple.tupleNumber, inserted.tuple.tupleNumber)
      assertState(conn, col, create.tuple, Version.HEAD.number)
      val next = encode(col, id, version + 4)
      val nextFrozen = freeze(listOf(next))
      val updated = upsert(db, conn, col, listOf(next)).single()
      assertEquals(create.tuple.tupleNumber, updated.previousTupleNumber)
      assertEquals(Action.UPDATE, updated.tuple.action)
      assertState(conn, col, updated.tuple, Version.HEAD.number)
      if (col.storeHistory) assertState(conn, col, create.tuple, updated.tuple.version)
      val explicit = encode(col, id, version + 8, Action.DELETE)
      val explicitFrozen = freeze(listOf(explicit))
      val tombstone = db.rows_delete_with_new_state(conn, col, version + 8,
        listOf(NewTuple(explicit.tuple, updated.tuple.tupleNumber))).single()
      assertEquals(updated.tuple.tupleNumber, tombstone.previousTupleNumber)
      assertState(conn, col, explicit.tuple, Version.HEAD.number)
      val recreate = encode(col, id, version + 12)
      val recreated = upsert(db, conn, col, listOf(NewTuple(recreate.tuple, create.tuple.tupleNumber))).single()
      assertNull(recreated.previousTupleNumber)
      assertEquals(Action.CREATE, recreated.tuple.action)
      assertState(conn, col, recreated.tuple, Version.HEAD.number)
      val before = oracle(conn, col)
      val savepoint = conn.jdbc.setSavepoint()
      val deleted = db.rows_delete_by_id_with_result(conn, col, version + 16, listOf(Id(id))).single()
      assertEquals(recreated.tuple.tupleNumber, deleted.previousTupleNumber)
      assertEquals(Action.DELETE, deleted.tuple.action)
      assertState(conn, col, deleted.tuple, Version.HEAD.number)
      assertEquals(deleted.tuple.tupleNumber, db.rows_purge(conn, col, version + 20, listOf(Id(id))).single()?.tupleNumber)
      conn.jdbc.rollback(savepoint)
      conn.jdbc.releaseSavepoint(savepoint)
      assertEquals(before, oracle(conn, col))
      assertEquals(frozen, freeze(listOf(create)))
      assertEquals(nextFrozen, freeze(listOf(next)))
      assertEquals(explicitFrozen, freeze(listOf(explicit)))
      assertFalse(conn.autoCommit)
      assertFalse(conn.isClosed())
    }
  }

  private fun fixture(committed: Boolean = false, partitions: Int = 1, changeCount: Boolean = false,
    test: (PgDatabase, PgConnection, PgCollection, Long) -> Unit) {
    val url = System.getenv("NAKSHA_TEST_MUTATION_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
    val config = PgConfig(Platform.getTestStorageId()).apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    val db = storage.database
    val catalog = PgCatalog(Tuple.encodeObject(NakshaCatalog(storage.id, "mutation_correctness"), db.pgAdminCatalogs.naksha, 1L).tuple)
    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      conn.autoCommit = false
      try {
        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '60s'").close()
        db.catalog_get_or_create_schema(conn, catalog.name, catalog.id.text)
        for (history in listOf(false, true)) {
          val definition = NakshaCollection("features_$history", catalog.id.text).apply {
            databaseId = storage.id.text
            members = MemberList(Member("name"))
            if (changeCount) members!!.add(StandardMembers.ChangeCount)
            indices = IndexList()
            shift = 12
            this.partitions = partitions
            storeHistory = if (history) StoreMode.ON else StoreMode.OFF
          }
          PgCollection.resolveLayout(definition)
          val col = PgCollection(catalog.id, Tuple.encodeRaw(definition, db.id.number, catalog.id.intValue,
            Id.COLLECTIONS_COL_ID.intValue, db.pgAdminCollections.naksha.validatedMembers, 1L, requestedAction = Action.CREATE).tuple)
          db.collection_create_table_if_not_exists(conn, catalog.name, col.name, col.columns, col.indices,
            col.creationOptions(config, db.maxTupleSize), catalog.id.text, col.id.text)
          val version = (PgPartitions.currentVersion(conn) and -4L) + 4L
          if (history) db.collection_maintain_partitions(conn, catalog.name, col.name, version)
          if (committed) conn.commit()
          test(db, conn, col, version)
        }
      } finally {
        conn.rollback()
        if (committed) {
          db.catalog_drop_schema(conn, catalog.name)
          conn.commit()
        }
      }
      assertEquals(emptyList(), strings(conn, "SELECT nspname AS value FROM pg_namespace WHERE nspname = ${PgUtil.quoteLiteral(catalog.name)}"))
    }
  }

  private fun encode(col: PgCollection, id: String, version: Long, action: Action = Action.CREATE): NewTuple =
    Tuple.encodeRaw(NakshaFeature(id).apply { properties.setRaw("name", "payload-$version") },
      col.tupleNumber.databaseNumber, col.catalogId.intValue, col.id.intValue,
      col.naksha.validatedMembers, version, requestedAction = action)

  private fun insert(db: PgDatabase, conn: PgConnection, col: PgCollection, inputs: List<NewTuple>) =
    db.rows_insert(conn, col.catalogId, col.id, inputs, col.storeHistory, col.columns)

  private fun upsert(db: PgDatabase, conn: PgConnection, col: PgCollection, inputs: List<NewTuple>) =
    db.rows_upsert(conn, col.catalogId, col.id, inputs, col.storeHistory, col.columns)

  private fun oracle(conn: PgConnection, col: PgCollection): List<String> =
    strings(conn, "SELECT row_to_json(r)::text AS value FROM ${col.qualifiedName} r ORDER BY fn, nv, version")

  private fun strings(conn: PgConnection, sql: String): List<String> {
    val result = ArrayList<String>()
    conn.execute(sql).use { cursor -> while (cursor.next()) result.add(cursor.get<String>("value")) }
    return result
  }

  private fun assertState(conn: PgConnection, col: PgCollection, tuple: Tuple, nv: Long) {
    conn.execute("SELECT * FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(tuple.tupleNumber.featureNumber)} AND nv = $nv").use { cursor ->
      assertTrue(cursor.next())
      assertEquals(tuple.version, cursor.get<Long>("version"))
      assertEquals(nv, cursor.get<Long>("nv"))
      assertContentEquals(tuple.featureBytes, cursor.column(assertNotNull(col.column(StandardMembers.FeatureBytes)).columnName) as ByteArray)
      assertEquals(tuple.membersBook["name"], cursor.column(assertNotNull(col.column(Member("name"))).columnName))
      assertFalse(cursor.next())
    }
    if (!col.storeHistory) assertEquals(1, strings(conn,
      "SELECT version::text AS value FROM ${col.qualifiedName} WHERE fn = ${Id.featureNumberToOrdinal(tuple.tupleNumber.featureNumber)}").size)
  }

  private fun freeze(inputs: List<NewTuple>): List<String> {
    val result = ArrayList<String>()
    for (input in inputs) {
      val tuple = input.tuple
      val values = ArrayList<String?>()
      values.add(input.previousTupleNumber?.toString())
      values.add(tuple.featureBytes.contentToString())
      for (i in 0 until tuple.membersBook.namesLength()) {
        values.add(tuple.membersBook.getNameAt(i))
        values.add(Platform.toJSON(tuple.membersBook[i]))
      }
      result.add(values.toString())
    }
    return result
  }
}
