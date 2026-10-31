@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import naksha.base.Platform
import naksha.model.IMemberProcessor
import naksha.model.IReadSession
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.XyzMembers
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadFeatures
import naksha.model.request.ResultFilter
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlCallbackSafetyTest {
  @Test
  fun `processor cannot commit its request`() = rejectedBoundary(true) { writer, _ -> writer.commit() }

  @Test
  fun `processor cannot roll back its request`() = rejectedBoundary(true) { writer, _ -> writer.rollback() }

  @Test
  fun `processor cannot close its session`() = rejectedBoundary(true) { writer, _ -> writer.close() }

  @Test
  fun `processor cannot execute a nested write`() = rejectedBoundary(true) { writer, collection ->
    writer.executeWrite(request(collection, "nested"))
  }

  @Test
  fun `result filter cannot commit its request`() = rejectedBoundary(false) { writer, _ -> writer.commit() }

  @Test
  fun `result filter cannot roll back its request`() = rejectedBoundary(false) { writer, _ -> writer.rollback() }

  @Test
  fun `result filter cannot close its session`() = rejectedBoundary(false) { writer, _ -> writer.close() }

  @Test
  fun `result filter cannot execute a nested write`() = rejectedBoundary(false) { writer, collection ->
    writer.executeWrite(request(collection, "nested"))
  }

  @Test
  fun `caught boundary errors allow callbacks to finish and empty nested writes are rejected`() = withWriter { _, writer, collection ->
    for (processor in arrayOf(true, false)) {
      val id = "caught-$processor"
      val callback = {
        assertThrows(IllegalStateException::class.java) { writer.commit() }
        assertThrows(IllegalStateException::class.java) { writer.rollback() }
        assertThrows(IllegalStateException::class.java) { writer.close() }
        assertThrows(IllegalStateException::class.java) { writer.executeWrite(WriteRequest()) }
        assertSame(writer.getTransaction(), writer.useTransaction())
        assertEquals(1, read(writer, collection, "00123").length)
      }
      val request = request(collection, id)
      if (processor) writer.processors.addProcessor(XyzMembers.XyzAuthor, IMemberProcessor { _, _, _, _, value -> callback(); value })
      else request.resultFilters.add(filter { callback() })
      writer.executeWrite(request)
      writer.processors.clear()
      writer.commit()
      assertEquals(1, read(writer, collection, id).length)
    }
  }

  @Test
  fun `validation errors and empty requests always clear the executing flag`() = withWriter { _, writer, collection ->
    assertThrows(Exception::class.java) { writer.executeWrite(WriteRequest().add(Write())) }
    assertNull(writer.getTransaction())
    writer.executeWrite(WriteRequest())
    writer.executeWrite(request(collection, "valid"))
    writer.rollback()
    writer.executeWrite(request(collection, "retried"))
    writer.commit()
    assertEquals(0, read(writer, collection, "valid").length)
    assertEquals(1, read(writer, collection, "retried").length)
  }

  @Test
  fun `close synchronously releases duplicate transaction leases and session leases`() = withWriter { fixture, writer, _ ->
    val session = writer.acquireSessionLock("session")
    val first = writer.acquireTransactionLock("duplicate")
    val second = writer.acquireTransactionLock("duplicate")
    val other = writer.acquireTransactionLock("other")
    writer.close()
    for (lock in arrayOf(session, first, second, other)) { assertTrue(lock.isClosed()); lock.close() }
    for (id in arrayOf("session", "duplicate", "other")) assertUnlocked(fixture, id)
    assertTrue(writer.isClosed())
  }

  @Test
  fun `rollback retains original failure and aggregates all cleanup failures`() = withWriter { fixture, writer, _ ->
    val session = writer.acquireSessionLock("session")
    val first = writer.acquireTransactionLock("duplicate")
    val second = writer.acquireTransactionLock("duplicate")
    val rollbackError = SQLException("rollback failed after rollback")
    val unlockErrors = arrayOf(SQLException("first unlock failed"), SQLException("second unlock failed"))
    val closeError = SQLException("close failed after close")
    var unlocks = 0
    interceptConnection(writer) { method, args ->
      when {
        method == "rollback" && args.isNullOrEmpty() -> throw rollbackError
        method == "prepareStatement" && args?.get(0).toString().contains("pg_advisory_unlock") -> throw unlockErrors[unlocks++]
        method == "close" -> throw closeError
      }
    }
    val error = assertThrows(SQLException::class.java) { writer.close() }
    assertSame(rollbackError, error)
    assertEquals(2, unlocks)
    assertSame(unlockErrors[0], error.suppressed.single())
    assertEquals(listOf(unlockErrors[1], closeError), error.suppressed[0].suppressed.toList())
    assertTrue(writer.isClosed())
    for (lock in arrayOf(session, first, second)) assertTrue(lock.isClosed())
    assertUnlocked(fixture, "session")
  }

  @Test
  fun `cleanup failure after durable commit never rolls back committed writes`() = withWriter { fixture, writer, collection ->
    writer.executeWrite(request(collection, "durable"))
    val lease = writer.acquireTransactionLock("durable")
    val closeError = SQLException("close failed after close")
    var rollbacks = 0
    interceptConnection(writer) { method, _ ->
      if (method == "rollback") rollbacks++
      if (method == "close") throw closeError
    }
    assertSame(closeError, assertThrows(SQLException::class.java) { writer.commit() })
    assertEquals(0, rollbacks)
    assertTrue(lease.isClosed())
    assertNull(writer.getTransaction())
    assertUnlocked(fixture, "durable")
    fixture.storage.newReadSession().use { reader -> assertEquals(1, read(reader, collection, "durable").length) }
  }

  private fun rejectedBoundary(processor: Boolean, boundary: (ImlWriteSession, NakshaCollection) -> Unit) = withWriter { fixture, writer, collection ->
    writer.executeWrite(request(collection, "pending"))
    val transaction = writer.useTransaction()
    val previousStatistics = Platform.toJSON(transaction)
    var calls = 0
    val callback = {
      calls++
      assertSame(transaction, writer.getTransaction())
      assertSame(transaction, writer.useTransaction())
      assertEquals(1, read(writer, collection, "pending").length)
      if (calls == 2) boundary(writer, collection)
    }
    val failing = request(collection, "pending", "failing", "unreached")
    if (processor) writer.processors.addProcessor(XyzMembers.XyzAuthor, IMemberProcessor { _, _, _, _, value -> callback(); value })
    else failing.resultFilters.add(filter { callback() })
    val error = assertThrows(IllegalStateException::class.java) { writer.executeWrite(failing) }
    assertTrue(error.message.orEmpty().contains("IML"))
    assertEquals(0, error.suppressed.size)
    writer.processors.clear()
    assertEquals(2, calls)
    assertFalse(writer.isClosed())
    assertSame(transaction, writer.getTransaction())
    assertEquals(previousStatistics, Platform.toJSON(transaction))
    assertEquals("original", read(writer, collection, "pending").featureTupleList[0]?.feature?.properties?.getRaw("state"))
    for (id in arrayOf("failing", "unreached", "nested")) assertEquals(0, read(writer, collection, id).length)
    fixture.storage.newReadSession().use { reader ->
      assertEquals(0, read(reader, collection, "pending").length)
      writer.executeWrite(request(collection, "after"))
      writer.commit()
      for (id in arrayOf("pending", "after")) assertEquals(1, read(reader, collection, id).length)
      for (id in arrayOf("failing", "unreached", "nested")) assertEquals(0, read(reader, collection, id).length)
    }
  }

  private fun request(collection: NakshaCollection, vararg ids: String) = WriteRequest().apply {
    returnResults = true
    strictOrder = true
    for (id in ids) add(Write().upsertFeature(collection, NakshaFeature(id).apply {
      properties.setRaw("state", if (ids.size == 1) "original" else "changed")
    }))
  }

  private fun filter(callback: () -> Unit) = object : ResultFilter {
    override fun filter(featureTuple: FeatureTuple): FeatureTuple { callback(); return featureTuple }
  }

  private fun read(session: IReadSession, collection: NakshaCollection, id: String): SuccessResponse {
    val response = session.executeRead(ReadFeatures().apply {
      catalogId = collection.catalogId
      collectionId = collection.id
      featureIds.add(id)
    }) as SuccessResponse
    session.loadTuples(response.featureTupleList)
    return response
  }

  private fun assertUnlocked(fixture: ImlTestFixture, id: String) {
    val key = "iml-lock:${fixture.metadataSchema}:$id"
    fixture.connection.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 0))").use {
      it.setString(1, key)
      it.executeQuery().use { rows -> assertTrue(rows.next()); assertTrue(rows.getBoolean(1), id) }
    }
    fixture.connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))").use {
      it.setString(1, key)
      it.executeQuery().use { rows -> assertTrue(rows.next()); assertTrue(rows.getBoolean(1)) }
    }
  }

  private fun interceptConnection(writer: ImlWriteSession, after: (String, Array<out Any?>?) -> Unit) {
    val field = ImlWriteSession::class.java.getDeclaredField("connection").apply { isAccessible = true }
    val connection = field.get(writer) as Connection
    field.set(writer, Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
      val result = try { method.invoke(connection, *(args ?: emptyArray())) } catch (error: InvocationTargetException) { throw error.targetException }
      after(method.name, args)
      result
    })
  }

  private fun withWriter(block: (ImlTestFixture, ImlWriteSession, NakshaCollection) -> Unit) = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    fixture.storage.newWriteSession().use { writer -> block(fixture, writer, NakshaCollection(fixture.layer, fixture.schema)) }
  }
}
