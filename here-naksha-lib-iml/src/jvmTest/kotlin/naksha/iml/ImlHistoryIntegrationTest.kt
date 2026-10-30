package naksha.iml

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.objects.StandardMembers
import naksha.model.objects.StoreMode
import naksha.model.objects.XyzMembers
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImlHistoryIntegrationTest {
  @Test
  fun `history metadata advertises history and deleted states`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val catalog = checkNotNull(session.getCatalogById(fixture.schema))
      val collection = checkNotNull(session.getCollectionById(catalog, fixture.layer))
      assertEquals(StoreMode.ON, collection.storeHistory)
      assertEquals(StoreMode.ON, collection.storeDeleted)
    }
  }

  @Test
  fun `finite snapshots use half open intervals and HEAD returns one logical state`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      for ((sequence, number, packedVersion) in listOf(
        Triple(1L, 1L, Version.manual(1, Action.CREATE).number),
        Triple(6L, 1L, Version.manual(1, Action.CREATE).number),
        Triple(7L, 11L, Version.manual(7, Action.UPDATE).number))) {
        val response = read(session, fixture, "00123") { version = bound(sequence) }
        assertStates(session, response, "00123", setOf(number to packedVersion))
      }
      assertEquals(0, read(session, fixture, "00123") { version = 0L }.length)
      for (maximum in listOf(null, Version.HEAD.number)) {
        val response = read(session, fixture, "00123") { version = maximum }
        assertStates(session, response, "00123", setOf(11L to Version.manual(7, Action.UPDATE).number))
      }
    }
  }

  @Test
  fun `multiple versions select latest states per logical ID despite distinct row numbers`() = ImlTestFixture.withFixture { fixture ->
    insertChains(fixture)
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture, "live", "other") { versions = 2 }
      assertEquals(4, response.length)
      session.loadTuples(response.featureTupleList)
      val actual = mutableMapOf<String, MutableSet<Pair<Long, Long>>>()
      for (entry in response.featureTupleList) {
        val state = checkNotNull(entry)
        actual.getOrPut(checkNotNull(state.id)) { mutableSetOf() }.add(state.tupleNumber.featureNumber to state.tupleNumber.version)
      }
      assertEquals(mapOf(
        "live" to setOf(103L to Version.manual(30, Action.UPDATE).number, 102L to Version.manual(20, Action.UPDATE).number),
        "other" to setOf(202L to Version.manual(25, Action.UPDATE).number, 201L to Version.manual(15, Action.CREATE).number)
      ), actual)
    }
  }

  @Test
  fun `history window includes both sequence bounds and excludes states outside them`() = ImlTestFixture.withFixture { fixture ->
    insertChains(fixture)
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture, "live") {
        versions = 3
        minVersion = bound(10)
        version = bound(20)
      }
      assertStates(session, response, "live", setOf(101L to Version.manual(10, Action.CREATE).number,
        102L to Version.manual(20, Action.UPDATE).number))
      assertStates(session, read(session, fixture, "live") {
        versions = 3
        minVersion = bound(20)
        version = bound(20)
      }, "live", setOf(102L to Version.manual(20, Action.UPDATE).number))
      assertStates(session, read(session, fixture, "live") {
        minVersion = bound(20)
        version = bound(25)
      }, "live", setOf(102L to Version.manual(20, Action.UPDATE).number))
      assertEquals(0, read(session, fixture, "live") {
        version = bound(25)
        minVersion = bound(21)
      }.length)
    }
  }

  @Test
  fun `finite version without history filters only HEAD with no historical fallback`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      assertEquals(0, read(session, fixture, "00123") {
        queryHistory = false
        version = bound(6)
      }.length)
      assertStates(session, read(session, fixture, "00123") {
        queryHistory = false
        version = bound(7)
      }, "00123", setOf(11L to Version.manual(7, Action.UPDATE).number))
      assertStates(session, read(session, fixture, "00123") { version = bound(6) },
        "00123", setOf(1L to Version.manual(1, Action.CREATE).number))
    }
  }

  @Test
  fun `single snapshot suppresses deletion without reviving an older state`() = ImlTestFixture.withFixture { fixture ->
    insertChains(fixture)
    fixture.storage.newReadSession().use { session ->
      for (maximum in listOf(bound(30), Version.HEAD.number)) {
        assertEquals(0, read(session, fixture, "gone") { version = maximum }.length)
        assertStates(session, read(session, fixture, "gone") {
          version = maximum
          queryDeleted = true
        }, "gone", setOf(303L to Version.manual(30, Action.DELETE).number))
      }
      assertStates(session, read(session, fixture, "gone") { version = bound(29) },
        "gone", setOf(302L to Version.manual(20, Action.UPDATE).number))
    }
  }

  @Test
  fun `newest matching tombstone suppresses entire history group unless deleted states requested`() = ImlTestFixture.withFixture { fixture ->
    insertChains(fixture)
    fixture.storage.newReadSession().use { session ->
      assertEquals(0, read(session, fixture, "gone") { versions = 3 }.length)
      assertStates(session, read(session, fixture, "gone") {
        versions = 3
        queryDeleted = true
      }, "gone", setOf(301L to Version.manual(10, Action.CREATE).number,
        302L to Version.manual(20, Action.UPDATE).number, 303L to Version.manual(30, Action.DELETE).number))
      assertStates(session, read(session, fixture, "gone") {
        versions = 3
        version = bound(20)
      }, "gone", setOf(301L to Version.manual(10, Action.CREATE).number, 302L to Version.manual(20, Action.UPDATE).number))
    }
  }

  @Test
  fun `older tombstones consume rank slots without refilling them`() = ImlTestFixture.withFixture { fixture ->
    fixture.insert("revived", 10, "I", "match", null, 401, nextVersion = 20)
    fixture.insert("revived", 20, "D", "match", null, 402, nextVersion = 30)
    fixture.insert("revived", 30, "I", "match", null, 403)
    fixture.storage.newReadSession().use { session ->
      assertStates(session, read(session, fixture, "revived") { versions = 2 },
        "revived", setOf(403L to Version.manual(30, Action.CREATE).number))
      assertStates(session, read(session, fixture, "revived") {
        versions = 2
        queryDeleted = true
      }, "revived", setOf(403L to Version.manual(30, Action.CREATE).number, 402L to Version.manual(20, Action.DELETE).number))
    }
  }

  @Test
  fun `relational AUTHOR filter runs before history ranking and deletion suppression`() = ImlTestFixture.withFixture { fixture ->
    insertChains(fixture)
    fixture.storage.newReadSession().use { session ->
      for (id in arrayOf("live", "gone")) {
        assertStates(session, read(session, fixture) {
          versions = 2
          queryMembers = And(Equals(StandardMembers.Id, id), Equals(XyzMembers.XyzAuthor, "match"))
        }, id, setOf((if (id == "live") 101L else 301L) to Version.manual(10, Action.CREATE).number,
          (if (id == "live") 102L else 302L) to Version.manual(20, Action.UPDATE).number))
      }
      assertStates(session, read(session, fixture) {
        versions = 2
        queryMembers = And(Equals(StandardMembers.Id, "00123"), Equals(XyzMembers.XyzAuthor, "old-author"))
      }, "00123", setOf(1L to Version.manual(1, Action.CREATE).number))
      assertEquals(0, read(session, fixture) {
        queryMembers = And(Equals(StandardMembers.Id, "00123"), Equals(XyzMembers.XyzAuthor, "old-author"))
      }.length)
    }
  }

  @Test
  fun `unknown source action remains selectable in snapshots and history`() = ImlTestFixture.withFixture { fixture ->
    fixture.insert("unknown", 2, "X", "old-unknown", null, 501, nextVersion = 12)
    fixture.storage.newReadSession().use { session ->
      assertStates(session, read(session, fixture, "unknown") { version = bound(2) },
        "unknown", setOf(501L to Version.manual(2, Action.VERSION).number))
      assertStates(session, read(session, fixture, "unknown") { versions = 2 },
        "unknown", setOf(501L to Version.manual(2, Action.VERSION).number, 16L to Version.manual(12, Action.VERSION).number))
    }
  }

  @Test
  fun `auto version bounds are rejected by the session read API`() = ImlTestFixture.withFixture { fixture ->
    val auto = Version.auto(2026, 1, 1, 1, Action.VERSION).number
    fixture.storage.newReadSession().use { session ->
      for (historical in arrayOf(false, true)) {
        for (minimum in arrayOf(false, true)) {
          val error = assertThrows(NakshaException::class.java) {
            read(session, fixture, "00123") {
              queryHistory = historical
              if (minimum) minVersion = auto else version = auto
            }
          }
          assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.error.code)
        }
      }
    }
  }

  @Test
  fun `exact historical load preserves interval without fabricating a successor identity`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture, "00123") { version = bound(6) }
      val selected = checkNotNull(response.featureTupleList.single())
      assertNull(selected.tuple)
      val expected = TupleNumber(fixture.storage.number, Id.textToInt(fixture.schema), Id.textToInt(fixture.layer),
        1, Version.manual(1, Action.CREATE).number)
      assertEquals(expected, selected.tupleNumber)
      session.loadTuples(response.featureTupleList)
      val tuple = checkNotNull(selected.tuple)
      assertEquals(bound(7), tuple.nextVersion)
      assertEquals("old-author", tuple.getString(XyzMembers.XyzAuthor))
      assertNull(selected.feature?.properties?.xyz?.getRaw("nuuid"))
      val fabricated = FeatureTuple(TupleNumber.copy(expected, version = bound(7)), null)
      session.loadTuples(listOf(fabricated))
      assertNull(fabricated.tuple)
      assertNull(fabricated.feature)
      assertStates(session, read(session, fixture, "00123") { version = bound(7) },
        "00123", setOf(11L to Version.manual(7, Action.UPDATE).number))
    }
  }

  @Test
  fun `removed selected historical row stays missing rather than loading its successor`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val response = read(session, fixture, "00123") { version = bound(6) }
      val selected = checkNotNull(response.featureTupleList.single())
      assertNull(selected.tuple)
      fixture.connection.prepareStatement("DELETE FROM ${fixture.sqlTable} WHERE i = ? AND version = ?").use {
        it.setLong(1, 1)
        it.setLong(2, 1)
        assertEquals(1, it.executeUpdate())
      }
      session.loadTuples(response.featureTupleList)
      assertNull(selected.tuple)
      assertNull(selected.feature)
      assertEquals(1L, selected.tupleNumber.featureNumber)
      assertEquals(Version.manual(1, Action.CREATE).number, selected.tupleNumber.version)
      assertStates(session, read(session, fixture, "00123"),
        "00123", setOf(11L to Version.manual(7, Action.UPDATE).number))
    }
  }

  private fun insertChains(fixture: ImlTestFixture) {
    fixture.insert("live", 10, "I", "match", null, 101, nextVersion = 20)
    fixture.insert("live", 20, "U", "match", null, 102, nextVersion = 30)
    fixture.insert("live", 30, "U", "new-author", null, 103)
    fixture.insert("other", 15, "I", "match", null, 201, nextVersion = 25)
    fixture.insert("other", 25, "U", "match", null, 202)
    fixture.insert("gone", 10, "I", "match", null, 301, nextVersion = 20)
    fixture.insert("gone", 20, "U", "match", null, 302, nextVersion = 30)
    fixture.insert("gone", 30, "D", "deleter", null, 303)
  }

  private fun read(session: ImlReadSession, fixture: ImlTestFixture, vararg ids: String,
    configure: ReadFeatures.() -> Unit = {}): SuccessResponse = session.executeRead(ReadFeatures().apply {
    catalogId = fixture.schema
    collectionId = fixture.layer
    queryHistory = true
    featureIds.addAll(ids)
    configure()
  }) as SuccessResponse

  private fun assertStates(session: ImlReadSession, response: SuccessResponse, id: String, expected: Set<Pair<Long, Long>>) {
    assertEquals(expected.size, response.length)
    session.loadTuples(response.featureTupleList)
    val actual = mutableSetOf<Pair<Long, Long>>()
    for (entry in response.featureTupleList) {
      val state = checkNotNull(entry)
      assertEquals(id, checkNotNull(state.tuple).id)
      assertEquals(id, state.feature?.id)
      actual.add(state.tupleNumber.featureNumber to state.tupleNumber.version)
    }
    assertEquals(expected, actual)
  }

  private fun bound(sequence: Long) = Version.manual(sequence, Action.VERSION).number
}
