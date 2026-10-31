@file:Suppress("OPT_IN_USAGE", "DEPRECATION")

package naksha.iml

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Version
import naksha.base.proxy
import naksha.model.SessionOptions
import naksha.model.objects.NakshaTx
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadHandle
import naksha.model.request.FeatureTuple
import naksha.model.request.OrderBy
import naksha.model.request.ReadTransactions
import naksha.model.request.ResultFilter
import naksha.model.request.ops.Equals
import naksha.model.request.query.SortOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlTransactionsTest {
  @Test
  fun `missing optional log is rejected without creating it`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      assertThrows(NakshaException::class.java) { ImlTransactions.read(session, ReadTransactions()) }
    }
    fixture.connection.prepareStatement("SELECT to_regclass(?)").use {
      it.setString(1, table(fixture))
      it.executeQuery().use { rows -> assertTrue(rows.next()); assertNull(rows.getString(1)) }
    }
  }

  @Test
  fun `transaction tuples preserve packed IDs bodies authors and transaction timestamps`() = ImlTestFixture.withFixture(quotedNames = true) { fixture ->
    createLog(fixture)
    val original = insert(fixture, 7, "alice")
    fixture.storage.newReadSession().use { session ->
      val response = ImlTransactions.read(session, ReadTransactions().readVersion(Version.manual(7, Action.VERSION)))
      assertEquals(1, response.length)
      val entry = checkNotNull(response.featureTupleList[0])
      val tn = entry.tupleNumber
      assertEquals(fixture.storage.number, tn.databaseNumber)
      assertEquals(Id.ADMIN_CATALOG_INT, tn.catalogNumber)
      assertEquals(Id.TRANSACTIONS_COL_INT, tn.collectionNumber)
      assertEquals(Version.manual(7, Action.VERSION).number, tn.featureNumber)
      assertEquals(Version.manual(7, Action.CREATE).number, tn.version)
      assertEquals(Version.HEAD.number, entry.tuple?.nextVersion)
      val tx = checkNotNull(entry.feature).proxy(NakshaTx::class)
      assertEquals(original.id, tx.id)
      assertEquals(original.time, tx.time)
      assertEquals(original.version, tx.version)
      assertEquals(original.featuresModified, tx.featuresModified)
      assertEquals("naksha.Tx", tx.featureType)
      assertEquals("kept", tx.properties.getRaw("custom"))
      assertEquals("alice", entry.tuple?.membersBook?.get(XyzMembers.XyzAuthor.name))
      assertEquals(tx.time, entry.tuple?.membersBook?.get(XyzMembers.XyzCreatedAt.name))
      assertEquals(tx.time, entry.tuple?.membersBook?.get(XyzMembers.XyzUpdatedAt.name))
      session.loadTuples(response.featureTupleList)
      assertEquals(original.id, entry.feature?.id)
    }
  }

  @Test
  fun `query compilation selects IDs authors versions and deterministic order`() = ImlTestFixture.withFixture { fixture ->
    createLog(fixture)
    insert(fixture, 7, "alice")
    insert(fixture, 8, "bob")
    insert(fixture, 9, "alice")
    fixture.storage.newReadSession().use { session ->
      val ordered = ImlTransactions.read(session, ReadTransactions().apply {
        queryMembers = Equals("author", "alice")
        orderBy = OrderBy("version", SortOrder.DESCENDING)
        limit = 1
      })
      assertEquals(Version.manual(9, Action.VERSION).toString(), ordered.featureTupleList[0]?.feature?.id)
      val bounded = ImlTransactions.read(session, ReadTransactions().apply {
        minVersion = Version.manual(8, Action.VERSION).number
        version = Version.manual(8, Action.VERSION).number
      })
      assertEquals(1, bounded.length)
      assertEquals(Version.manual(8, Action.VERSION).toString(), bounded.featureTupleList[0]?.feature?.id)
      assertEquals(0, ImlTransactions.read(session, ReadTransactions().apply { featureIds.add(ImlTestFixture.INJECTION_ID) }).length)
    }
  }

  @Test
  fun `filters run after selection refill the limit and publish eager snapshots`() = ImlTestFixture.withFixture { fixture ->
    createLog(fixture)
    for (sequence in 7L..10L) insert(fixture, sequence, "alice")
    val options = SessionOptions(appId = "iml-transactions")
    fixture.storage.newReadSession(options).use { session ->
      val response = ImlTransactions.read(session, ReadTransactions().apply {
        limit = 1
        returnHandle = true
        resultFilters.add(object : ResultFilter {
          override fun filter(featureTuple: FeatureTuple): FeatureTuple? =
            if (featureTuple.feature?.id == Version.manual(7, Action.VERSION).toString()) null else featureTuple
        })
      })
      assertEquals(Version.manual(8, Action.VERSION).toString(), response.featureTupleList[0]?.feature?.id)
      val handle = response.getRaw("handle") as String
      fixture.connection.createStatement().use { it.execute("DELETE FROM ${table(fixture)}") }
      val next = fixture.storage.handles.read(ReadHandle(handle).apply { returnHandle = true }, options, fixture.storage.hardCap)
      assertEquals(Version.manual(9, Action.VERSION).toString(), next.featureTupleList[0]?.feature?.id)
      assertNotNull(next.featureTupleList[0]?.tuple)
      assertNotNull(next.getRaw("handle"))
      assertThrows(NakshaException::class.java) { fixture.storage.handles.read(ReadHandle(handle), options, fixture.storage.hardCap) }
    }
  }

  @Test
  fun `read uses supplied session connection and does not commit its uncommitted log`() = ImlTestFixture.withFixture { fixture ->
    createLog(fixture)
    val storage = fixture.storage
    fixture.connection.autoCommit = false
    try {
      insert(fixture, 7, "alice")
      val session = object : ImlReadSession(storage, SessionOptions(appId = "sticky-test")) {
        override fun <T> withConnection(block: (java.sql.Connection) -> T): T = block(fixture.connection)
      }
      session.use { assertEquals(1, ImlTransactions.read(it, ReadTransactions()).length) }
      storage.newReadSession().use { assertEquals(0, ImlTransactions.read(it, ReadTransactions()).length) }
      assertFalse(fixture.connection.autoCommit)
      fixture.connection.rollback()
      storage.newReadSession().use { assertEquals(0, ImlTransactions.read(it, ReadTransactions()).length) }
    } finally {
      fixture.connection.rollback()
      fixture.connection.autoCommit = true
    }
  }

  @Test
  fun `invalid request and inconsistent transaction identity fail explicitly`() = ImlTestFixture.withFixture { fixture ->
    createLog(fixture)
    insert(fixture, 7, null)
    fixture.storage.newReadSession().use { session ->
      assertThrows(NakshaException::class.java) { ImlTransactions.read(session, ReadTransactions().apply { collectionId = "roads" }) }
      assertThrows(NakshaException::class.java) { ImlTransactions.read(session, ReadTransactions().apply { limit = 0 }) }
      fixture.connection.createStatement().use { it.execute("UPDATE ${table(fixture)} SET source_version = 8") }
      assertThrows(NakshaException::class.java) { ImlTransactions.read(session, ReadTransactions()) }
    }
  }

  private fun table(fixture: ImlTestFixture): String = "${ImlMetadata.quote(fixture.metadataSchema)}.iml_transactions"

  private fun createLog(fixture: ImlTestFixture) {
    fixture.connection.createStatement().use { it.execute("CREATE TABLE ${table(fixture)} (source_version bigint PRIMARY KEY, feature jsonb NOT NULL)") }
  }

  private fun insert(fixture: ImlTestFixture, sequence: Long, author: String?): NakshaTx {
    val transaction = NakshaTx().apply {
      setRaw("id", Version.manual(sequence, Action.VERSION).toString())
      setRaw("time", 1700000000000L + sequence)
      featuresModified = 2
      properties.setRaw("custom", "kept")
      properties.xyz.setRaw("author", author)
    }
    fixture.connection.prepareStatement("INSERT INTO ${table(fixture)} VALUES (?, ?::jsonb)").use {
      it.setLong(1, sequence)
      it.setString(2, Platform.toJSON(transaction))
      it.executeUpdate()
    }
    return transaction
  }
}
