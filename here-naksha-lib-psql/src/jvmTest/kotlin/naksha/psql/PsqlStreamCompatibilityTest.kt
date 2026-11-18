package naksha.psql

import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.SessionOptions
import naksha.model.Tuple
import naksha.model.objects.NakshaAdmin
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaTx
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTransaction
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Compatibility regressions for the unified collection root and transaction metadata; no database required. */
class PsqlStreamCompatibilityTest {
  private val admin = NakshaAdmin(Id("stream_test"))
  private val storage = Mockito.mock(PgStorage::class.java)
  private val session = PsqlStreamSession(storage, SessionOptions())
  private val conn = Mockito.mock(PgConnection::class.java)
  private val sql = ArrayList<String>()
  private val plans = ArrayList<PgPlan>()
  private val cursors = ArrayList<PgCursor>()

  private fun collection(): PgCollection {
    PgCollection.resolveLayout(admin.collections)
    val definition = NakshaCollection("roads", "stream_catalog")
    definition.databaseId = "stream_test"
    PgCollection.resolveLayout(definition)
    return PgCollection(Id(definition.catalogId!!), Tuple.encodeObject(definition, admin.collections, version = 4L).tuple)
  }

  private fun request(collection: PgCollection, history: Boolean = true): StreamRequest =
    StreamRequest(Id("stream_test"), collection.catalogId, collection.id, queryHistory = history, chunkSize = 7)

  private fun cursor(collection: PgCollection, tuples: List<Tuple>): PgCursor {
    val rows = PgRows().withCollection(collection)
    for (i in tuples.indices) rows[i] = tuples[i]
    var index = 0
    val cursor = Mockito.mock(PgCursor::class.java)
    cursors.add(cursor)
    Mockito.`when`(cursor.fetch()).thenReturn(cursor)
    Mockito.`when`(cursor.isRow()).thenAnswer { index < rows.size }
    Mockito.`when`(cursor.next()).thenAnswer { ++index < rows.size }
    Mockito.`when`(cursor.columnNames()).thenReturn(rows.columns.map { it.sqlAlias }.toTypedArray())
    Mockito.`when`(cursor.column(Mockito.anyString())).thenAnswer { invocation ->
      val name = invocation.getArgument<String>(0)
      rows.columns.first { it.sqlAlias == name }.values[index]
    }
    return cursor
  }

  private fun queries(result: (String) -> PgCursor) {
    Mockito.doAnswer { invocation ->
      val statement = invocation.getArgument<String>(0)
      sql.add(statement)
      val cursor = result(statement)
      val plan = Mockito.mock(PgPlan::class.java)
      plans.add(plan)
      Mockito.`when`(plan.execute(Mockito.any())).thenReturn(cursor)
      plan
    }.`when`(conn).prepare(Mockito.anyString(), Mockito.any())
  }

  @Test
  fun featureStreamUsesQualifiedRootAndOrdinalRecovery() {
    val collection = collection()
    val tuple = Tuple.encodeObject(NakshaFeature("256"), collection.naksha, version = 4L).tuple
    queries { cursor(collection, listOf(tuple)) }
    val stream = PsqlStreamSession.PerFeatureStream(session, request(collection), conn, collection)
    val chunk = stream.next()
    assertEquals("256", chunk.tuples.single().id.text)
    assertEquals("256", chunk.tuples.single().feature.proxy(NakshaFeature::class).id)
    assertEquals(1, sql.size)
    assertTrue("FROM ${collection.qualifiedName}" in sql.single())
    assertFalse("UNION" in sql.single())
    chunk.setTargets(1)
    chunk.acknowledge()
    assertEquals(Id.featureNumberToOrdinal(tuple.tupleNumber.featureNumber) + 1,
      (stream.getRecoveryRequest().getRaw("psqlCursor") as Number).toLong())
    stream.close()
    for (plan in plans) Mockito.verify(plan).close()
    for (cursor in cursors) Mockito.verify(cursor).close()
    Mockito.verify(conn).close()
  }

  @Test
  fun headOnlyStreamFiltersTheUnifiedRootByNextVersion() {
    val collection = collection()
    queries { cursor(collection, emptyList()) }
    PsqlStreamSession.PerFeatureStream(session, request(collection, history = false), conn, collection).use { stream ->
      assertFalse(stream.hasNext())
    }
    assertTrue("nv = ${Version.HEAD.number}" in sql.single())
    assertTrue("FROM ${collection.qualifiedName}" in sql.single())
  }

  @Test
  fun transactionStreamUsesCatalogsAndCurrentAdminDescriptor() {
    val collection = collection()
    PgCollection.resolveLayout(admin.transactions)
    val txns = PgCollection(Id(admin.id), Tuple.encodeObject(admin.transactions, admin.collections, version = 4L).tuple)
    val tx = NakshaTx().setNow(1L)
    tx.useCatalog(collection.catalogId).useCollection(collection.id)
    val audit = Tuple.encodeObject(tx, txns.naksha, version = tx.version.number).tuple
    queries { statement ->
      if ("FROM ${txns.qualifiedName}" in statement) cursor(txns, listOf(audit))
      else cursor(collection, emptyList())
    }
    PsqlStreamSession.PerTransactionStream(session, request(collection), conn, txns, collection).use { stream ->
      val chunk = stream.next()
      assertTrue(chunk is StreamTransaction)
      assertEquals(tx.version.number, chunk.version)
      chunk.setTargets(1)
      chunk.acknowledge()
      assertFalse(stream.hasNext())
    }
    assertEquals(2, sql.size)
    assertTrue("FROM ${txns.qualifiedName}" in sql.first())
    assertTrue("nv = ${Version.HEAD.number}" in sql.first())
    for (plan in plans) Mockito.verify(plan).close()
    for (cursor in cursors) Mockito.verify(cursor).close()
  }

  @Test
  fun closedSessionDoesNotOpenAConnection() {
    session.close()
    assertFailsWith<NakshaException> { session.read(request(collection())) }
    Mockito.verifyNoInteractions(storage)
  }
}
