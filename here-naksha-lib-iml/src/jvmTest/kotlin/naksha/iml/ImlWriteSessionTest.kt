@file:Suppress("OPT_IN_USAGE", "DEPRECATION")

@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.util.UUID
import naksha.base.Action
import naksha.base.NakshaException
import naksha.base.NakshaError
import naksha.base.Platform
import naksha.base.Version
import naksha.geo.SpPoint
import naksha.model.IMemberProcessor
import naksha.model.IReadSession
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.StoreMode
import naksha.model.objects.StandardIndices
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.XyzMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlWriteSessionTest {
  @Test
  fun `create update delete recreate purge and history use relational row identities`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val original = NakshaFeature("writer-00123").withGeometry(SpPoint(1.0, 2.0, 3.0))
    original.properties.setRaw("custom", "unchanged")
    val json = Platform.toJSON(original)
    val created = write(writer, Write().createFeature(collection, original))
    val createdTn = checkNotNull(created.featureTupleList[0]).tupleNumber
    assertEquals(Action.CREATE, createdTn.action)
    assertEquals(json, Platform.toJSON(original))
    assertEquals(writer.useTransaction().version.number ushr 2, createdTn.version ushr 2)
    assertTrue((createdTn.version ushr 2) > 12)
    assertEquals(1, read(writer, collection, original.id).length)
    fixture.storage.newReadSession().use { assertEquals(0, read(it, collection, original.id).length) }
    writer.commit()
    assertNull(writer.getTransaction())

    val updated = write(writer, Write().updateFeature(collection, NakshaFeature(original.id), false))
    val updatedTn = checkNotNull(updated.featureTupleList[0]).tupleNumber
    assertEquals(Action.UPDATE, updatedTn.action)
    assertNotEquals(createdTn.featureNumber, updatedTn.featureNumber)
    assertTrue(updatedTn.version > createdTn.version)
    writer.commit()
    assertEquals(2, read(writer, collection, original.id, history = true).length)
    val deleted = write(writer, Write().deleteFeatureById(collection, original.id, Version(updatedTn.version)))
    assertEquals(Action.DELETE, checkNotNull(deleted.featureTupleList[0]).tupleNumber.action)
    assertEquals(0, read(writer, collection, original.id).length)
    assertEquals(1, read(writer, collection, original.id, deleted = true).length)
    writer.commit()
    assertEquals(Action.CREATE, checkNotNull(write(writer, Write().createFeature(collection, original)).featureTupleList[0]).tupleNumber.action)
    writer.commit()
    write(writer, Write().purgeFeatureById(collection, original.id))
    writer.commit()
    assertEquals(0, read(writer, collection, original.id, history = true, deleted = true).length)
  }

  @Test
  fun `same transaction updates coalesce without self history and retain create action`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val first = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("coalesced"))).featureTupleList[0]).tupleNumber
    val second = checkNotNull(write(writer, Write().upsertFeature(collection, NakshaFeature("coalesced").apply { properties.setRaw("value", 2) })).featureTupleList[0]).tupleNumber
    assertEquals(first, second)
    assertEquals(Action.CREATE, second.action)
    assertEquals(1, writer.useTransaction().featuresModified)
    writer.commit()
    assertEquals(1, read(writer, collection, "coalesced", history = true).length)
    val response = read(writer, collection, "coalesced")
    writer.loadTuples(response.featureTupleList)
    assertEquals(2, checkNotNull(response.featureTupleList[0]?.feature).properties.getAs("value", Int::class))
  }

  @Test
  fun `transaction version tampering rejects subsequent writes and commit`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("tampered")))
    writer.useTransaction().setRaw("id", Version.manual(1000, Action.VERSION).toString())
    assertThrows(NakshaException::class.java) { write(writer, Write().createFeature(collection, NakshaFeature("after-tampering"))) }
    assertThrows(NakshaException::class.java) { writer.commit() }
    assertNull(writer.getTransaction())
    assertEquals(0, read(writer, collection, "tampered").length)
    assertEquals(0, read(writer, collection, "after-tampering").length)
    write(writer, Write().createFeature(collection, NakshaFeature("fresh-transaction")))
    writer.commit()
    write(writer, Write().createFeature(collection, NakshaFeature("commit-tampering")))
    writer.getTransaction()!!.setRaw("id", Version.manual(1000, Action.VERSION).toString())
    assertThrows(NakshaException::class.java) { writer.commit() }
    assertEquals(0, read(writer, collection, "commit-tampering").length)
  }

  @Test
  fun `transaction time tampering rejects writes and commit without persisting changes`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("time-tampering")))
    val transaction = writer.getTransaction()!!
    transaction.setRaw("time", transaction.time + 1000)
    assertThrows(NakshaException::class.java) { write(writer, Write().createFeature(collection, NakshaFeature("after-time-tampering"))) }
    assertThrows(NakshaException::class.java) { writer.commit() }
    assertNull(writer.getTransaction())
    assertEquals(0, read(writer, collection, "time-tampering").length)
    write(writer, Write().createFeature(collection, NakshaFeature("commit-time-tampering")))
    writer.getTransaction()!!.setRaw("time", "invalid")
    assertThrows(NakshaException::class.java) { writer.commit() }
    assertEquals(0, read(writer, collection, "commit-time-tampering").length)
  }

  @Test
  fun `session locks stay outside idle transactions with persistent timeout settings`() = withWriter { fixture, _ ->
    (fixture.storage.newWriteSession(SessionOptions(appId = "idle-lock", idleTxTimeout = 100, stmtTimeout = 1000, lockTimeout = 200)) as ImlWriteSession).use { writer ->
      val lock = writer.acquireSessionLock("idle-session")
      val field = ImlWriteSession::class.java.getDeclaredField("lockConnection").apply { isAccessible = true }
      val conn = field.get(writer) as java.sql.Connection
      assertTrue(conn.autoCommit)
      writer.stmtTimeout = 750
      writer.lockTimeout = 150
      conn.createStatement().use {
        it.executeQuery("SELECT current_setting('statement_timeout'), current_setting('lock_timeout'), current_setting('idle_in_transaction_session_timeout')").use { rows ->
          assertTrue(rows.next()); assertEquals("750ms", rows.getString(1)); assertEquals("150ms", rows.getString(2)); assertEquals("0", rows.getString(3))
        }
      }
      Thread.sleep(250)
      assertFalse(lock.isClosed())
      assertFalse(tryLock(fixture, "idle-session"))
      lock.close()
      assertTrue(tryLock(fixture, "idle-session"))
    }
  }

  @Test
  fun `lost session lock connection invalidates all leases and rejects protected writes`() = withWriter { fixture, writer ->
    val first = writer.acquireSessionLock("lost-first")
    val second = writer.acquireSessionLock("lost-second")
    write(writer, Write().createFeature(NakshaCollection(fixture.layer, fixture.schema), NakshaFeature("lost-lock-write")))
    val field = ImlWriteSession::class.java.getDeclaredField("lockConnection").apply { isAccessible = true }
    val conn = field.get(writer) as java.sql.Connection
    val pid = conn.createStatement().use { it.executeQuery("SELECT pg_backend_pid()").use { rows -> rows.next(); rows.getInt(1) } }
    fixture.connection.prepareStatement("SELECT pg_terminate_backend(?)").use { it.setInt(1, pid); it.executeQuery().close() }
    assertTrue(first.isClosed())
    assertTrue(second.isClosed())
    assertThrows(NakshaException::class.java) { writer.useTransaction() }
    assertThrows(NakshaException::class.java) { writer.executeWrite(WriteRequest()) }
    assertThrows(NakshaException::class.java) { writer.commit() }
    fixture.storage.newReadSession().use { assertEquals(0, read(it, NakshaCollection(fixture.layer, fixture.schema), "lost-lock-write").length) }
  }

  @Test
  fun `contended transaction lock fails fast after writer gate and leaves transaction usable`() = withWriter { fixture, writer ->
    val key = "iml-lock:${fixture.metadataSchema}:external-lock"
    fixture.connection.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?,0))").use { it.setString(1, key); it.executeQuery().close() }
    try {
      writer.useTransaction()
      writer.lockTimeout = 0
      writer.stmtTimeout = 0
      val started = System.nanoTime()
      val failure = assertThrows(NakshaException::class.java) { writer.acquireTransactionLock("external-lock") }
      assertEquals(NakshaError.CONFLICT, failure.error.code)
      assertTrue(System.nanoTime() - started < 1_000_000_000L)
      write(writer, Write().createFeature(NakshaCollection(fixture.layer, fixture.schema), NakshaFeature("after-lock-conflict")))
      writer.commit()
    } finally {
      fixture.connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,0))").use { it.setString(1, key); it.executeQuery().close() }
    }
  }

  @Test
  fun `processor transaction tampering cannot change allocated row version`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("before-tampering")))
    writer.processors.addProcessor(XyzMembers.XyzAuthor, IMemberProcessor { _, _, _, _, value ->
      writer.getTransaction()!!.setRaw("id", Version.manual(1000, Action.VERSION).toString())
      value
    })
    assertThrows(NakshaException::class.java) { write(writer, Write().createFeature(collection, NakshaFeature("during-tampering"))) }
    assertThrows(NakshaException::class.java) { writer.commit() }
    assertEquals(0, read(writer, collection, "before-tampering").length)
    assertEquals(0, read(writer, collection, "during-tampering").length)
  }

  @Test
  fun `savepoint rollback failure prevents partial request commit`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("earlier")))
    val field = ImlWriteSession::class.java.getDeclaredField("connection").apply { isAccessible = true }
    val real = field.get(writer) as java.sql.Connection
    val failing = java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection::class.java.classLoader, arrayOf(java.sql.Connection::class.java)) { _, method, args ->
      if (method.name == "rollback" && args?.size == 1) throw java.sql.SQLException("Injected savepoint rollback failure")
      try { method.invoke(real, *(args ?: emptyArray())) } catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
    } as java.sql.Connection
    field.set(writer, failing)
    val failure = assertThrows(NakshaException::class.java) {
      writer.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("partial"))).add(Write().createFeature(collection, NakshaFeature("earlier"))))
    }
    assertEquals(1, failure.suppressed.size)
    assertThrows(NakshaException::class.java) { writer.commit() }
    assertNull(writer.getTransaction())
    assertEquals(0, read(writer, collection, "partial").length)
    assertEquals(0, read(writer, collection, "earlier").length)
  }

  @Test
  fun `timeout setters update sticky connections and timed out reads leave transaction usable`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    writer.acquireSessionLock("timeout-connection")
    write(writer, Write().createFeature(collection, NakshaFeature("timeout-survivor")))
    writer.socketTimeout = 2000
    writer.lockTimeout = 75
    writer.stmtTimeout = 50
    writer.withConnection { conn ->
      assertEquals(2000, conn.networkTimeout)
      conn.createStatement().use { statement ->
        statement.executeQuery("SELECT current_setting('statement_timeout'), current_setting('lock_timeout')").use {
          assertTrue(it.next()); assertEquals("50ms", it.getString(1)); assertEquals("75ms", it.getString(2))
        }
      }
    }
    assertThrows(java.sql.SQLException::class.java) { writer.withConnection { conn -> conn.createStatement().use { it.executeQuery("SELECT pg_sleep(0.2)").close() } } }
    writer.stmtTimeout = 1000
    assertEquals(1, read(writer, collection, "timeout-survivor").length)
    writer.commit()
  }

  @Test
  fun `failed request rolls back only its changes and optimistic identity checks are exact`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("keep")))
    assertThrows(NakshaException::class.java) {
      writer.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("undo"))).add(Write().createFeature(collection, NakshaFeature("keep"))))
    }
    assertEquals(0, read(writer, collection, "undo").length)
    assertEquals(1, read(writer, collection, "keep").length)
    assertEquals(1, writer.useTransaction().featuresModified)
    assertThrows(NakshaException::class.java) { write(writer, Write().deleteFeatureById(collection, "keep", Version.manual(1, Action.VERSION))) }
    val current = checkNotNull(read(writer, collection, "keep").featureTupleList[0]).tupleNumber
    val other = naksha.base.TupleNumber(current.databaseNumber, current.catalogNumber, current.collectionNumber, current.featureNumber + 1, current.version)
    assertThrows(NakshaException::class.java) { write(writer, Write().deleteFeatureById(collection, "keep").withAtomic(true).withTupleNumber(other)) }
    writer.commit()
    assertEquals(1, read(writer, collection, "keep").length)
    assertEquals(0, read(writer, collection, "undo").length)
  }

  @Test
  fun `rollback and close discard pending features and transaction records are committed atomically`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("rollback")))
    val sequence = writer.useTransaction().version.number ushr 2
    writer.rollback()
    assertNull(writer.getTransaction())
    assertEquals(0, read(writer, collection, "rollback").length)
    write(writer, Write().createFeature(collection, NakshaFeature("committed")))
    val committed = writer.useTransaction().version.number ushr 2
    writer.commit()
    fixture.connection.prepareStatement("SELECT source_version FROM ${ImlMetadata.quote(fixture.metadataSchema)}.iml_transactions").use {
      it.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(committed, rows.getLong(1)); assertFalse(rows.next()) }
    }
    assertTrue(committed > sequence)
    write(writer, Write().createFeature(collection, NakshaFeature("closed")))
    writer.close()
    writer.close()
    assertThrows(IllegalStateException::class.java) { writer.useTransaction() }
    fixture.storage.newReadSession().use { assertEquals(0, read(it, collection, "closed").length) }
  }

  @Test
  fun `map and collection metadata is private until commit and rollback restores discovery`() = withWriter { fixture, writer ->
    val mapId = "imlw_${UUID.randomUUID().toString().replace("-", "")}"
    val collection = NakshaCollection("items", mapId)
    val existingReader = fixture.storage.newReadSession()
    try {
      assertNull(existingReader.getCatalogById(mapId))
      writer.executeWrite(WriteRequest().add(Write().createMap(NakshaCatalog(mapId))).add(Write().createCollection(collection)).add(Write().createFeature(collection, NakshaFeature("one"))))
      val catalog = checkNotNull(writer.getCatalogById(mapId))
      assertNotNull(writer.getCollectionById(catalog, collection.id))
      assertEquals(1, read(writer, collection, "one").length)
      fixture.storage.newReadSession().use { assertNull(it.getCatalogById(mapId)) }
      writer.rollback()
      assertNull(writer.getCatalogById(mapId))
      writer.executeWrite(WriteRequest().add(Write().createMap(NakshaCatalog(mapId))).add(Write().createCollection(collection)).add(Write().createFeature(collection, NakshaFeature("one"))))
      writer.commit()
      val visibleMap = checkNotNull(existingReader.getCatalogById(mapId))
      assertNotNull(existingReader.getCollectionById(visibleMap, collection.id))
      assertEquals(1, read(existingReader, collection, "one").length)
      fixture.storage.newReadSession().use { reader ->
        val map = checkNotNull(reader.getCatalogById(mapId))
        assertNotNull(reader.getCollectionById(map, collection.id))
        assertEquals(1, read(reader, collection, "one").length)
      }
      write(writer, Write().deleteCollectionById(mapId, collection.id))
      assertNull(writer.getCollectionById(checkNotNull(writer.getCatalogById(mapId)), collection.id))
      writer.rollback()
      assertNotNull(writer.getCollectionById(checkNotNull(writer.getCatalogById(mapId)), collection.id))
      write(writer, Write().deleteMapById(mapId))
      writer.commit()
      assertNull(writer.getCatalogById(mapId))
    } finally {
      existingReader.close()
      writer.rollback()
      // The fixture owns only its original schemas; remove this independently named writer schema as well.
      fixture.connection.createStatement().use { it.execute("DROP SCHEMA IF EXISTS ${ImlMetadata.quote(mapId)} CASCADE") }
    }
  }

  @Test
  fun `failed administrative batch rolls back DDL and preserves earlier pending writes`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("earlier")))
    val added = NakshaCollection("request-rollback", fixture.schema)
    assertThrows(NakshaException::class.java) {
      writer.executeWrite(WriteRequest().add(Write().createCollection(added)).add(Write().createCollection(added)))
    }
    assertNull(writer.getCollectionById(checkNotNull(writer.getCatalogById(fixture.schema)), added.id))
    assertEquals(1, read(writer, collection, "earlier").length)
    writer.commit()
  }

  @Test
  fun `processors execute declared custom members in insertion order without mutating input`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("processed", fixture.schema).apply {
      members = MemberList(XyzMembers.ALL).apply { add(Member("custom")) }
    }
    write(writer, Write().createCollection(collection))
    val calls = ArrayList<String>()
    writer.processors.addProcessor("custom", IMemberProcessor { _, _, _, _, value -> calls.add("first"); "$value-a" })
    writer.processors.addProcessor("custom", IMemberProcessor { _, _, _, _, value -> calls.add("second"); "$value-b" })
    val input = NakshaFeature("processed").apply { properties.setRaw("custom", "start") }
    val result = write(writer, Write().createFeature(collection, input))
    assertEquals(listOf("first", "second"), calls)
    assertEquals("start", input.properties.getRaw("custom"))
    assertEquals("start-a-b", result.featureTupleList[0]?.feature?.properties?.getRaw("custom"))
    writer.commit()
    val read = read(writer, collection, input.id)
    writer.loadTuples(read.featureTupleList)
    assertEquals("start-a-b", read.featureTupleList[0]?.feature?.properties?.getRaw("custom"))
  }

  @Test
  fun `processor failure rolls back its request but leaves previous writes committable`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    write(writer, Write().createFeature(collection, NakshaFeature("before-processor-failure")))
    writer.processors.addProcessor(XyzMembers.XyzAuthor, IMemberProcessor { _, _, _, _, _ -> throw IllegalArgumentException("processor failed") })
    assertThrows(IllegalArgumentException::class.java) { write(writer, Write().createFeature(collection, NakshaFeature("processor-failure"))) }
    assertEquals(0, read(writer, collection, "processor-failure").length)
    writer.commit()
    assertEquals(1, read(writer, collection, "before-processor-failure").length)
  }

  @Test
  fun `non super user can write features but cannot modify administrative objects`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = false
    try {
      (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
        assertThrows(NakshaException::class.java) { write(writer, Write().createCollection(NakshaCollection("forbidden", fixture.schema))) }
        assertNull(writer.getTransaction())
        write(writer, Write().createFeature(NakshaCollection(fixture.layer, fixture.schema), NakshaFeature("allowed")))
        writer.commit()
      }
    } finally { context.su = previous }
  }

  @Test
  fun `results honor returnResults input order and missing deletes are no ops`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val empty = writer.executeWrite(WriteRequest().add(Write().createFeature(collection, NakshaFeature("without-results")))) as SuccessResponse
    assertEquals(0, empty.length)
    val result = writer.executeWrite(WriteRequest().apply {
      returnResults = true
      strictOrder = true
      add(Write().createFeature(collection, NakshaFeature("z")))
      add(Write().createFeature(collection, NakshaFeature("a")))
      add(Write().deleteFeatureById(collection, "missing"))
    }) as SuccessResponse
    assertEquals(2, result.length)
    assertEquals("z", result.featureTupleList[0]?.feature?.id)
    assertEquals("a", result.featureTupleList[1]?.feature?.id)
    writer.rollback()
  }

  @Test
  fun `native locks cross connections release transaction locks at boundaries and session locks at close`() = withWriter { fixture, writer ->
    val sessionLock = writer.acquireSessionLock("session")
    val transactionLock = writer.acquireTransactionLock("transaction")
    assertFalse(sessionLock.isClosed())
    assertFalse(transactionLock.isClosed())
    assertFalse(tryLock(fixture, "session"))
    assertFalse(tryLock(fixture, "transaction"))
    writer.commit()
    assertTrue(transactionLock.isClosed())
    assertFalse(sessionLock.isClosed())
    assertTrue(tryLock(fixture, "transaction"))
    assertFalse(tryLock(fixture, "session"))
    val rollbackLock = writer.acquireTransactionLock("rollback")
    writer.rollback()
    assertTrue(rollbackLock.isClosed())
    assertTrue(tryLock(fixture, "rollback"))
    val explicit = writer.acquireTransactionLock("explicit")
    explicit.close()
    explicit.close()
    assertTrue(explicit.isClosed())
    assertTrue(tryLock(fixture, "explicit"))
    writer.close()
    assertTrue(sessionLock.isClosed())
    assertTrue(tryLock(fixture, "session"))
  }

  @Test
  fun `lock timeout leaves the session usable and explicit unlock permits another writer`() = withWriter { fixture, writer ->
    val held = writer.acquireSessionLock("contended")
    (fixture.storage.newWriteSession(SessionOptions(appId = "second-writer", lockTimeout = 100, stmtTimeout = 1000)) as ImlWriteSession).use { second ->
      assertThrows(java.sql.SQLException::class.java) { second.acquireSessionLock("contended") }
      held.close()
      second.acquireSessionLock("contended").use { assertFalse(it.isClosed()) }
    }
  }

  @Test
  fun `reserved schemas external catalog deletion and unsupported collection options fail safely`() = withWriter { fixture, writer ->
    assertThrows(NakshaException::class.java) { write(writer, Write().createMap(NakshaCatalog("public"))) }
    assertThrows(NakshaException::class.java) { write(writer, Write().deleteMapById(fixture.schema).withForce(true)) }
    assertThrows(NakshaException::class.java) { write(writer, Write().createCollection(NakshaCollection("partitioned", fixture.schema, partitions = 2))) }
    assertNotNull(writer.getCatalogById(fixture.schema))
    writer.rollback()
  }

  @Test
  fun `purge removes retained history without HEAD and atomically matches the latest retained identity`() = withWriter { fixture, writer ->
    val collection = NakshaCollection("history-only", fixture.schema, storeDeleted = StoreMode.OFF)
    write(writer, Write().createCollection(collection))
    writer.commit()
    val first = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("purged"))).featureTupleList[0]).tupleNumber
    val second = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("atomic"))).featureTupleList[0]).tupleNumber
    writer.commit()
    write(writer, Write().deleteFeatureById(collection, "purged"))
    write(writer, Write().deleteFeatureById(collection, "atomic"))
    writer.commit()
    assertEquals(0, read(writer, collection, "purged").length)
    assertEquals(1, read(writer, collection, "purged", history = true).length)
    val purged = checkNotNull(write(writer, Write().purgeFeatureById(collection, "purged")).featureTupleList[0]).tupleNumber
    assertEquals(first.featureNumber, purged.featureNumber)
    assertEquals(Action.DELETE, purged.action)
    assertThrows(NakshaException::class.java) { write(writer, Write().purgeFeatureById(collection, "atomic", Version.manual(1, Action.VERSION))) }
    write(writer, Write().purgeFeatureById(collection, "atomic").withAtomic(true).withTupleNumber(second))
    writer.commit()
    assertEquals(0, read(writer, collection, "purged", history = true, deleted = true).length)
    assertEquals(0, read(writer, collection, "atomic", history = true, deleted = true).length)
  }

  @Test
  fun `lock acquisition rejects self conflicts and inverted lifetime order before blocking`() = withWriter { _, writer ->
    writer.acquireSessionLock("same-key").use {
      assertThrows(NakshaException::class.java) { writer.acquireTransactionLock("same-key") }
      assertNull(writer.getTransaction())
      writer.useTransaction()
      assertThrows(NakshaException::class.java) { writer.acquireSessionLock("new-session-key") }
      assertThrows(NakshaException::class.java) { writer.acquireSessionLock("same-key") }
      writer.rollback()
    }
    writer.acquireTransactionLock("same-key").close()
    writer.rollback()
  }

  @Test
  fun `all effective members convert to declared numeric types and reject invalid values`() = withWriter { fixture, writer ->
    val types = arrayOf(MemberType.INT8, MemberType.INT16, MemberType.INT32, MemberType.INT64, MemberType.FLOAT32, MemberType.FLOAT64)
    val expected = arrayOf(Byte::class, Short::class, Int::class, Long::class, Float::class, Double::class)
    val collection = NakshaCollection("typed", fixture.schema).apply {
      members = MemberList(XyzMembers.ALL).apply {
        for (i in types.indices) add(Member("n$i", types[i]))
        add(Member("shape", MemberType.SPATIAL))
      }
      indices = IndexList()
    }
    write(writer, Write().createCollection(collection))
    writer.commit()
    val seen = ArrayList<Any?>()
    for (i in types.indices) writer.processors.addProcessor("n$i", IMemberProcessor { _, _, _, _, value -> seen.add(value); value })
    val feature = NakshaFeature("typed").apply {
      for (i in types.indices) properties.setRaw("n$i", 7)
      properties.setRaw("shape", Platform.fromJSON("""{"type":"Point","coordinates":[1,2,3]}"""))
      setRaw("referencePoint", Platform.fromJSON("""{"type":"Point","coordinates":[4,5,6]}"""))
    }
    val result = write(writer, Write().createFeature(collection, feature))
    for (i in expected.indices) assertTrue(expected[i].isInstance(seen[i]), "Processor value for ${types[i]}")
    assertNotNull(result.featureTupleList[0]?.tuple?.membersBook?.get("shape"))
    assertNotNull(result.featureTupleList[0]?.tuple?.membersBook?.get(XyzMembers.XyzReferencePoint.name))
    writer.commit()
    val loaded = read(writer, collection, feature.id)
    writer.loadTuples(loaded.featureTupleList)
    for (i in types.indices) assertTrue(types[i].isInstance(loaded.featureTupleList[0]?.tuple?.membersBook?.get("n$i")), "Reloaded ${types[i]}")
    writer.processors.clear()
    val unprocessed = write(writer, Write().createFeature(collection, feature.withId("without-processors")))
    for (i in types.indices) assertTrue(types[i].isInstance(unprocessed.featureTupleList[0]?.tuple?.membersBook?.get("n$i")), "Unprocessed ${types[i]}")
    for (invalid in arrayOf<Any>("wrong", 1.5, 128, Double.NaN)) {
      assertThrows(NakshaException::class.java) {
        write(writer, Write().createFeature(collection, NakshaFeature("invalid").apply { properties.setRaw("n0", invalid) }))
      }
    }
    writer.rollback()
  }

  @Test
  fun `first transaction rollback never reuses its returned tuple identity`() = withWriter { fixture, writer ->
    val collection = NakshaCollection(fixture.layer, fixture.schema)
    val first = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("same"))).featureTupleList[0]).tupleNumber
    writer.rollback()
    val second = checkNotNull(write(writer, Write().createFeature(collection, NakshaFeature("same"))).featureTupleList[0]).tupleNumber
    assertTrue((second.version ushr 2) > (first.version ushr 2))
    assertNotEquals(first, second)
    writer.commit()
  }

  @Test
  fun `configured public catalog accepts descriptor updates but refuses create and drop`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.schema = "public"
    fixture.config.writeEnabled = true
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = true
    try {
      (fixture.storage.newWriteSession() as ImlWriteSession).use { writer ->
        val descriptor = checkNotNull(writer.getCatalogById("public"))
        descriptor.properties.setRaw("writerDescriptor", "updated")
        write(writer, Write().updateMap(descriptor, false))
        writer.commit()
        assertEquals("updated", writer.getCatalogById("public")?.properties?.getRaw("writerDescriptor"))
        assertThrows(NakshaException::class.java) { write(writer, Write().createMap(NakshaCatalog("public"))) }
        assertThrows(NakshaException::class.java) { write(writer, Write().deleteMapById("public")) }
        writer.rollback()
      }
    } finally { context.su = previous }
  }

  @Test
  fun `unsupported unique and custom index definitions fail while standard spatial index is installed`() = withWriter { fixture, writer ->
    for (index in arrayOf(Index("custom", "custom"), Index("author", "author").apply { setRaw("unique", true) })) {
      val collection = NakshaCollection("rejected-index", fixture.schema).apply {
        members = MemberList(XyzMembers.ALL).apply { add(Member("custom")) }
        indices = IndexList(listOf(index))
      }
      assertThrows(NakshaException::class.java) { write(writer, Write().createCollection(collection)) }
      assertNull(writer.getCollectionById(checkNotNull(writer.getCatalogById(fixture.schema)), collection.id))
    }
    val collection = NakshaCollection("spatial-index", fixture.schema).apply { indices = IndexList(listOf(StandardIndices.Geometry)) }
    write(writer, Write().createCollection(collection))
    writer.commit()
    val layer = fixture.storage.metadata.getLayer(fixture.schema, collection.id)!!
    fixture.connection.prepareStatement("SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND tablename = ?").use {
      it.setString(1, layer.schema); it.setString(2, layer.table)
      it.executeQuery().use { rows ->
        var spatial = false
        while (rows.next()) if (rows.getString(1).contains("USING gist (geo)")) spatial = true
        assertTrue(spatial)
      }
    }
  }

  private fun write(session: ImlWriteSession, write: Write): SuccessResponse =
    session.executeWrite(WriteRequest().add(write).apply { returnResults = true }) as SuccessResponse

  private fun read(session: IReadSession, collection: NakshaCollection, id: String, history: Boolean = false, deleted: Boolean = false): SuccessResponse =
    session.executeRead(ReadFeatures().apply {
      catalogId = collection.catalogId
      collectionId = collection.id
      featureIds.add(id)
      queryHistory = history
      queryDeleted = deleted
      if (history) versions = 100
    }) as SuccessResponse

  private fun tryLock(fixture: ImlTestFixture, id: String): Boolean {
    val key = "iml-lock:${fixture.metadataSchema}:$id"
    val acquired = fixture.connection.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?,0))").use {
      it.setString(1, key); it.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
    }
    if (acquired) fixture.connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,0))").use { it.setString(1, key); it.executeQuery().close() }
    return acquired
  }

  private fun withWriter(block: (ImlTestFixture, ImlWriteSession) -> Unit) = ImlTestFixture.withFixture { fixture ->
    fixture.config.writeEnabled = true
    val context = NakshaContext.currentContext()
    val previous = context.su
    context.su = true
    try {
      (fixture.storage.newWriteSession(SessionOptions(appId = "iml-writer-test", author = "writer")) as ImlWriteSession).use { block(fixture, it) }
    } finally { context.su = previous }
  }
}
