package naksha.psql

import naksha.base.Action
import naksha.base.Guid
import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.Tuple
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import naksha.model.request.ReadFeatures
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_EMPTY_TOMBSTONE_DB_URL", matches = ".+")
class PgEmptyTombstoneReadTest : PgTestBase(collection = null) {
  init {
    assertEquals(System.getenv("NAKSHA_TEST_EMPTY_TOMBSTONE_DB_URL"), System.getenv("NAKSHA_TEST_PSQL_DB_URL"))
  }

  @Test fun publicFeaturesDecodeEmptyHeadAndArchivedTombstones() {
    for (history in listOf(false, true)) {
      useCollection(NakshaCollection("empty_tombstone_$history", catalog.id).apply {
        storeHistory = if (history) StoreMode.ON else StoreMode.OFF
      })
      val ids = listOf("hashed-empty-read", "42")
      for (id in ids) insertFeature(NakshaFeature(id).apply {
        title = "must not resurrect"
        setRaw("oldPayload", "must not resurrect")
        properties.setRaw("oldProperty", "must not resurrect")
      })
      val deleted = LinkedHashMap<Long, TupleNumber>()
      PgSession(storage, newSessionOptions(), false).use { session ->
        val version = session.useTransaction().version.number
        session.useConnNonNull(false) { conn ->
          val col = assertNotNull(session.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue, conn))
          for (id in ids) {
            val guid = storage.database.rows_delete_by_id_without_result(conn, col, version, listOf(Id(id)), true).single()
            deleted[Id(id).number] = guid.tupleNumber
          }
          assertOnlyKeys(conn, col, Version.HEAD.number)
        }
        session.commit()
      }

      assertTrue(executeRead(read()).features.isEmpty())
      assertTombstones(read().apply { queryDeleted = true }, deleted, null)

      var purgeVersion = 0L
      PgSession(storage, newSessionOptions(), false).use { session ->
        purgeVersion = session.useTransaction().version.number
        session.useConnNonNull(false) { conn ->
          val col = assertNotNull(session.getPgCollection(Id(catalog.id).intValue, Id(collection.id).intValue, conn))
          for (id in ids) {
            val expected = assertNotNull(deleted[Id(id).number])
            val again = storage.database.rows_delete_by_id_with_result(conn, col, purgeVersion, listOf(Id(id))).single()
            assertEquals(expected, again.tuple.tupleNumber)
            assertNull(again.previousTupleNumber)
            // Selectors may supply the original text without persisting it in the tombstone.
            assertEquals(id, again.tuple.decodeObject(null).getRaw("id"))
            assertEquals(id, again.tuple.decodeFeature(null).id)
            assertEquals(expected, again.tuple.decodeFeature(null).tupleNumber)
            assertEquals(expected, storage.database.rows_purge(conn, col, purgeVersion, listOf(Id(id))).single()?.tupleNumber)
            assertNull(storage.database.rows_purge(conn, col, purgeVersion + 4, listOf(Id(id))).single())
          }
          if (history) assertOnlyKeys(conn, col, purgeVersion)
        }
        session.commit()
      }
      assertTrue(executeRead(read().apply { queryDeleted = true }).features.isEmpty())
      if (history) {
        assertTombstones(read().apply {
          queryDeleted = true
          queryHistory = true
          version = deleted.values.first().version
        }, deleted, purgeVersion)
      }
    }
  }

  @Test fun emptyLiveTupleStillFailsDecoding() {
    val book = naksha.jbon.HeapBook(naksha.jbon.BookType.MEMBER_BOOK)
    book.put(StandardMembers.TN, TupleNumber(1L, 1, 1, 42L, 4L))
    assertFailsWith<Exception> { Tuple(byteArrayOf(), book).decodeFeature(null) }
  }

  private fun read() = ReadFeatures().apply {
    catalogId = collection.catalogId
    collectionId = collection.id
  }

  private fun assertTombstones(request: ReadFeatures, expected: Map<Long, TupleNumber>, nextVersion: Long?) {
    val response = executeRead(request)
    val features = response.features
    assertEquals(expected.size, features.size)
    val found = HashSet<TupleNumber>()
    for (entry in features) {
      val feature = assertNotNull(entry)
      val tn = assertNotNull(feature.tupleNumber)
      assertEquals(expected[tn.featureNumber], tn)
      assertTrue(found.add(tn))
      val id = if (tn.featureNumber >= 0L) tn.featureNumber.toString() else null
      assertTrue(feature.containsKey("id"), "Unknown identity must be explicit, not absent")
      assertEquals(id, feature.getRaw("id"))
      if (id == null) {
        assertFailsWith<NakshaException> { feature.id }
        assertNull(feature.getRaw("id"), "Typed access must not generate a random identifier")
      } else assertEquals(id, feature.id)
      val xyz = feature.properties.xyz
      val guid = assertNotNull(xyz.guid)
      assertEquals(Guid(id ?: "", tn), guid)
      assertEquals(guid, Guid.fromString(assertNotNull(xyz.uuid)))
      assertEquals(Action.DELETE, xyz.action)
      assertEquals(nextVersion ?: tn.version, assertNotNull(xyz.nguid).tupleNumber.version)
      assertEquals(id ?: "", xyz.nguid?.id)
      assertNull(feature.title)
      assertNull(feature.geometry)
      assertNull(feature.getRaw("oldPayload"))
      assertNull(feature.properties.getRaw("oldProperty"))
      val json = Platform.toJSON(feature)
      assertEquals(id, feature.getRaw("id"))
      assertTrue(!json.contains("must not resurrect"))
    }
    assertEquals(expected.values.toSet(), found)
  }

  private fun assertOnlyKeys(conn: PgConnection, col: PgCollection, nv: Long) {
    conn.execute("SELECT * FROM ${col.qualifiedName} WHERE nv = $nv AND (version & 3) = 2").use { cursor ->
      var count = 0
      while (cursor.next()) {
        count++
        for (column in col.columns) {
          if (column.columnName !in setOf("fn", "version", "nv")) assertNull(cursor.column(column.columnName), column.name)
        }
      }
      assertEquals(2, count)
    }
  }
}
