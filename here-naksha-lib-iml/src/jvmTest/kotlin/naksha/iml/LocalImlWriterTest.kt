package naksha.iml

import java.sql.SQLException
import naksha.base.Action
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Version
import naksha.geo.SpPoint
import naksha.model.Naksha
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalImlWriterTest {
  @Test
  fun `local URL validation accepts only explicit loopback hosts and approved properties`() {
    for (host in arrayOf("localhost", "127.0.0.1", "[::1]")) {
      ImlTestFixture.validateLocalUrl("jdbc:postgresql://$host/postgres")
      ImlTestFixture.validateLocalUrl("jdbc:postgresql://$host:5432/postgres?user=postgres&password=p%40ss%26word%3D%23&ssl=false&sslmode=disable&ApplicationName=local&connectTimeout=5&socketTimeout=10")
    }
  }

  @Test
  fun `local URL validation rejects remote implicit multiple and redirected hosts without connecting`() {
    for (url in arrayOf(
      "jdbc:postgresql:postgres", "jdbc:postgresql:///postgres", "jdbc:postgresql://remote.example/postgres",
      "jdbc:postgresql://127.0.0.2/postgres", "jdbc:postgresql://localhost.example/postgres",
      "jdbc:postgresql://localhost,remote.example/postgres", "jdbc:postgresql://localhost:5432,127.0.0.1:5432/postgres",
      "jdbc:postgresql://[::ffff:127.0.0.1]/postgres", "jdbc:postgresql://localhost@remote.example/postgres",
      "jdbc:postgresql://user:password@localhost/postgres", "jdbc:postgresql://localhost:0/postgres",
      "jdbc:postgresql://localhost:65536/postgres", "jdbc:postgresql://localhost/postgres#fragment",
      "jdbc:postgresql://%6cocalhost/postgres", "jdbc:postgresql://localhost./postgres",
      "jdbc:postgresql://localhost/postgres?PGHOST=remote.example",
      "jdbc:postgresql://localhost/postgres?host=remote.example",
      "jdbc:postgresql://localhost/postgres?%50%47HOST=remote.example",
      "jdbc:postgresql://localhost/postgres?PGPORT=5433",
      "jdbc:postgresql://localhost/postgres?service=remote",
      "jdbc:postgresql://localhost/postgres?socketFactory=custom.Factory",
      "jdbc:postgresql://localhost/postgres?socketFactoryArg=remote",
      "jdbc:postgresql://localhost/postgres?sslfactory=custom.Factory",
      "jdbc:postgresql://localhost/postgres?options=-c%20search_path=public",
      "jdbc:postgresql://localhost/postgres?ssl=true", "jdbc:postgresql://localhost/postgres?sslmode=require",
      "jdbc:postgresql://localhost/postgres?user=one&user=two",
      "jdbc:postgresql://localhost/postgres?password=%ZZ", "jdbc:postgresql://localhost/postgres?ssl",
      "postgresql://localhost/postgres"
    )) {
      assertThrows(IllegalArgumentException::class.java, { ImlTestFixture.validateLocalUrl(url) }, url)
    }
  }

  @Test
  fun `put update delete and resurrection are readable through snapshots and history`() = ImlTestFixture.withFixture(quotedNames = true) { fixture ->
    val writer = LocalImlWriter(fixture)
    val id = "new' ; --"
    assertNull(Naksha.getStorageById(fixture.config.id))
    writer.put(id, 20, "creator", body("first"), "POINT Z (1 2 3)")
    assertNull(Naksha.getStorageById(fixture.config.id), "Writing must not create storage")
    val storage = fixture.storage
    val created = read(fixture, id)
    val first = checkNotNull(created.featureTupleList.single())
    assertEquals(17L, first.tupleNumber.featureNumber)
    assertEquals(Version.manual(20, Action.CREATE).number, first.tupleNumber.version)
    assertEquals("creator", first.feature?.properties?.xyz?.getRaw("author"))
    assertEquals("first", first.feature?.properties?.getRaw("custom"))
    assertEquals(3.0, checkNotNull(first.feature?.geometry).asPoint().z)
    Naksha.cache.store(checkNotNull(first.tuple))
    assertNotNull(Naksha.cache[first.tupleNumber])

    writer.put(id, 30, "updater", body("second"), "POINT Z (4 5 6)")
    assertNull(Naksha.cache[first.tupleNumber], "Committed writes must invalidate cached intervals")
    val updated = checkNotNull(read(fixture, id).featureTupleList.single())
    assertEquals(18L, updated.tupleNumber.featureNumber)
    assertEquals(Version.manual(30, Action.UPDATE).number, updated.tupleNumber.version)
    assertEquals("updater", updated.feature?.properties?.xyz?.getRaw("author"))
    assertEquals("second", updated.feature?.properties?.getRaw("custom"))
    assertEquals(6.0, checkNotNull(updated.feature?.geometry).asPoint().z)
    val historical = checkNotNull(read(fixture, id) { version = bound(29) }.featureTupleList.single())
    assertEquals(first.tupleNumber, historical.tupleNumber)
    assertEquals(bound(30), historical.tuple?.nextVersion)
    assertEquals("creator", historical.tuple?.getString(XyzMembers.XyzAuthor))
    assertEquals("first", historical.feature?.properties?.getRaw("custom"))

    writer.delete(id, 40, "deleter")
    assertEquals(0, read(fixture, id).length)
    val deleted = checkNotNull(read(fixture, id) { queryDeleted = true }.featureTupleList.single())
    assertEquals(19L, deleted.tupleNumber.featureNumber)
    assertEquals(Version.manual(40, Action.DELETE).number, deleted.tupleNumber.version)
    assertEquals("deleter", deleted.feature?.properties?.xyz?.getRaw("author"))
    assertNull(deleted.feature?.geometry)
    assertEquals(Version.HEAD.number, deleted.tuple?.nextVersion)
    assertEquals(updated.tupleNumber, read(fixture, id) { version = bound(39) }.featureTupleList.single()?.tupleNumber)
    val history = read(fixture, id) { versions = 3; queryDeleted = true }
    assertEquals(setOf(first.tupleNumber, updated.tupleNumber, deleted.tupleNumber),
      history.featureTupleList.map { checkNotNull(it).tupleNumber }.toSet())
    assertEquals(0, read(fixture, id) { versions = 3 }.length)

    writer.put(id, 50, "reviver", body("third"))
    val revived = checkNotNull(read(fixture, id).featureTupleList.single())
    assertEquals(20L, revived.tupleNumber.featureNumber)
    assertEquals(Version.manual(50, Action.CREATE).number, revived.tupleNumber.version)
    assertNull(revived.feature?.geometry)
    val error = assertThrows(NakshaException::class.java) { storage.newWriteSession(null) }
    assertEquals(NakshaError.UNSUPPORTED_OPERATION, error.error.code)
  }

  @Test
  fun `older equal and malformed writes roll back without changing HEAD or allocating a row`() = ImlTestFixture.withFixture { fixture ->
    val writer = LocalImlWriter(fixture)
    writer.put("rollback", 20, "original", body("original"))
    val original = checkNotNull(read(fixture, "rollback").featureTupleList.single())
    Naksha.cache.store(checkNotNull(original.tuple))
    for (version in arrayOf(19L, 20L)) {
      assertThrows(IllegalArgumentException::class.java) { writer.put("rollback", version, "invalid", body("bad")) }
      assertThrows(IllegalArgumentException::class.java) { writer.delete("rollback", version, "invalid") }
    }
    assertThrows(SQLException::class.java) { writer.put("rollback", 30, "invalid", "{invalid") }
    assertThrows(SQLException::class.java) { writer.put("rollback", 30, "invalid", body("bad"), "not WKT") }
    assertThrows(IllegalArgumentException::class.java) { writer.put("rollback", Long.MAX_VALUE, "invalid", body("bad")) }
    for (version in arrayOf(-1L, Version.MAX_MANUAL.seq + 1)) {
      assertThrows(NakshaException::class.java) { writer.put("rollback", version, "invalid", body("bad")) }
    }
    assertTrue(fixture.connection.autoCommit)
    assertNotNull(Naksha.cache[original.tupleNumber], "Failed writes must not invalidate committed cache entries")
    fixture.connection.prepareStatement("SELECT version, next_version, author, i FROM ${fixture.sqlTable} WHERE id = ?").use {
      it.setString(1, "rollback")
      it.executeQuery().use { rows ->
        assertTrue(rows.next())
        assertEquals(20L, rows.getLong("version"))
        assertEquals(Long.MAX_VALUE, rows.getLong("next_version"))
        assertEquals("original", rows.getString("author"))
        assertEquals(17L, rows.getLong("i"))
        assertTrue(!rows.next())
      }
    }
    writer.put("different-id", 1, "independent", body("other"))
    assertEquals(18L, read(fixture, "different-id").featureTupleList.single()?.tupleNumber?.featureNumber)
    writer.put("rollback", 30, "valid", body("valid"))
    assertEquals(19L, read(fixture, "rollback").featureTupleList.single()?.tupleNumber?.featureNumber)
  }

  @Test
  fun `delete of an absent ID creates a tombstone and caller transactions are not committed`() = ImlTestFixture.withFixture { fixture ->
    val writer = LocalImlWriter(fixture)
    fixture.connection.autoCommit = false
    try {
      assertThrows(IllegalStateException::class.java) { writer.put("absent", 1, "author", body("ignored")) }
    } finally {
      fixture.connection.rollback()
      fixture.connection.autoCommit = true
    }
    writer.delete("absent", 1, "author")
    assertEquals(0, read(fixture, "absent").length)
    val tombstone = checkNotNull(read(fixture, "absent") { queryDeleted = true }.featureTupleList.single())
    assertEquals(Version.manual(1, Action.DELETE).number, tombstone.tupleNumber.version)
    assertEquals("author", tombstone.feature?.properties?.xyz?.getRaw("author"))
  }

  private fun body(value: String) = """{"type":"Feature","id":"ignored-json-id","properties":{"custom":"$value","@ns:com:here:xyz":{"author":"ignored-json-author"}}}"""

  private fun bound(sourceVersion: Long) = Version.manual(sourceVersion, Action.VERSION).number

  private fun read(fixture: ImlTestFixture, id: String, configure: ReadFeatures.() -> Unit = {}): SuccessResponse =
    fixture.storage.newReadSession().use { session ->
      val response = session.executeRead(ReadFeatures().apply {
        catalogId = fixture.schema
        collectionId = fixture.layer
        queryMembers = Equals(StandardMembers.Id, id)
        queryHistory = true
        configure()
      }) as SuccessResponse
      session.loadTuples(response.featureTupleList)
      response
    }
}
