package naksha.iml

import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadHandle
import naksha.model.request.ResultFilter
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals
import naksha.model.request.query.PQuery
import naksha.model.request.query.PAnd
import naksha.model.request.query.POr
import naksha.model.request.query.PNot
import naksha.model.request.query.IPropertyQuery
import naksha.model.request.query.Property
import naksha.model.request.query.StringOp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class ImlPagingTest {
  @BeforeEach
  fun context() {
    NakshaContext.currentContext().appId = "iml-paging-test"
  }

  @Test
  fun `handles are single use bounded expiring authenticated and storage local`() {
    var now = 0L
    val handles = ImlHandles(2, 6, 100) { now }
    val options = SessionOptions(appId = "owner", authToken = "secret")
    val tuples = (1L..3L).map {
      ImlTuple.encode(NakshaFeature("feature-$it"), TupleNumber(1, 1, 1, it, 4))
    }
    val first = handles.create(tuples, 1, options)
    // Native SuccessResponse exposes continuation tokens through its raw accessor, not a handle property.
    val token = checkNotNull(first.getRaw("handle") as? String)
    assertEquals(36, token.length)
    for (foreign in listOf(options.copy(appId = "foreign"), options.copy(authToken = "foreign"), options.copy(authToken = null))) {
      illegal { handles.read(ReadHandle(token), foreign, 100) }
    }
    illegal { ImlHandles().read(ReadHandle(token), options, 100) }
    illegal { handles.read(ReadHandle("garbled"), options, 100) }
    illegal { handles.read(ReadHandle(token).apply { limit = 0 }, options, 100) }
    handles.create(tuples, 1, options)
    illegal { handles.create(tuples, 1, options) }
    val second = handles.read(ReadHandle(token).apply { returnHandle = true }, options, 100)
    assertEquals(listOf("feature-2"), ids(second))
    illegal { handles.read(ReadHandle(token), options, 100) }
    val next = checkNotNull(second.getRaw("handle") as? String)
    now = 100
    illegal { handles.read(ReadHandle(next), options, 100) }
    val fresh = handles.create(tuples, 1, options)
    handles.clear()
    illegal { handles.read(ReadHandle(fresh.getRaw("handle") as String), options, 100) }
    val tupleBound = ImlHandles(128, 2)
    illegal { tupleBound.create(tuples, 1, options) }
    val overrideStore = ImlHandles()
    val overrideFirst = overrideStore.create(tuples, 1, options)
    val last = overrideStore.read(ReadHandle(overrideFirst.getRaw("handle") as String).apply { limit = 2 }, options, 100)
    assertEquals(listOf("feature-2", "feature-3"), ids(last))
    assertNull(last.getRaw("handle") as? String)
  }

  @Test
  fun `pages survive session closure source disappearance and cache clear with default and overridden sizes`() = ImlTestFixture.withFixture { fixture ->
    val storage = fixture.storage
    val options = SessionOptions(appId = "paging-owner", authToken = "paging-auth")
    val first = storage.newReadSession(options).use { session ->
      session.executeRead(request(fixture).apply { returnHandle = true; limit = 1 }) as SuccessResponse
    }
    assertEquals(listOf("00123"), ids(first))
    val token = checkNotNull(first.getRaw("handle") as? String)
    fixture.connection.createStatement().use { it.executeUpdate("DELETE FROM ${fixture.sqlTable}") }
    Naksha.cache.clear(storage)
    storage.newReadSession(options).use { session ->
      val second = session.executeRead(ReadHandle(token).apply { returnHandle = true }) as SuccessResponse
      assertEquals(listOf(ImlTestFixture.INJECTION_ID), ids(second))
      val third = session.executeRead(ReadHandle(second.getRaw("handle") as String).apply { limit = 2; returnHandle = true }) as SuccessResponse
      assertEquals(listOf("far", "null-geo"), ids(third))
      val last = session.executeRead(ReadHandle(third.getRaw("handle") as String)) as SuccessResponse
      assertEquals(listOf("unknown"), ids(last))
      assertNull(last.getRaw("handle") as? String)
      assertEquals("keep-me", last.features[0]?.properties?.xyz?.getRaw("unrecognized"))
    }
  }

  @Test
  fun `session auth and foreign storage cannot consume another owners token`() = ImlTestFixture.withFixture { fixture ->
    val storage = fixture.storage
    val options = SessionOptions(appId = "owner", authToken = "secret")
    val token = storage.newReadSession(options).use {
      (it.executeRead(request(fixture).apply { returnHandle = true; limit = 1 }) as SuccessResponse).getRaw("handle") as String
    }
    for (foreign in listOf(options.copy(appId = "other"), options.copy(authToken = "other"))) {
      storage.newReadSession(foreign).use { session -> illegal { session.executeRead(ReadHandle(token)) } }
    }
    val nextToken = storage.newReadSession(options).use { session ->
      val response = session.executeRead(ReadHandle(token).apply { returnHandle = true }) as SuccessResponse
      assertEquals(1, response.length)
      response.getRaw("handle") as String
    }
    Naksha.removeStorage(fixture.config)
    illegal { storage.handles.read(ReadHandle(nextToken), options, 100) }
  }

  @Test
  fun `history pages retain ordered exact revisions and stop at candidate hard cap`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.hardCap = 3
    val storage = fixture.storage
    var token: String? = null
    val versions = ArrayList<Long>()
    storage.newReadSession().use { session ->
      val first = session.executeRead(request(fixture).apply {
        queryHistory = true
        this.versions = 10
        queryDeleted = true
        returnHandle = true
        limit = 1
      }) as SuccessResponse
      versions.add(checkNotNull(first.featureTupleList[0]).tupleNumber.version)
      token = first.getRaw("handle") as? String
      // Mutating an exposed decoded feature must not mutate the snapshot's remaining tuples.
      first.features[0]?.properties?.setRaw("custom", "changed")
    }
    fixture.connection.createStatement().use { it.executeUpdate("DELETE FROM ${fixture.sqlTable}") }
    Naksha.cache.clear(storage)
    storage.newReadSession().use { session ->
      while (token != null) {
        val response = session.executeRead(ReadHandle(checkNotNull(token)).apply { returnHandle = true }) as SuccessResponse
        versions.add(checkNotNull(response.featureTupleList[0]).tupleNumber.version)
        assertEquals("kept", response.features[0]?.properties?.getRaw("custom"))
        token = response.getRaw("handle") as? String
      }
    }
    assertEquals(listOf(4L, 29L, 32L), versions)
  }

  @Test
  fun `LIFO filters receive materialized entries refill limits and freeze modified pages`() = ImlTestFixture.withFixture { fixture ->
    val storage = fixture.storage
    var calls = 0
    val filtered = request(fixture).apply {
      limit = 1
      resultFilters.add(resultFilter { entry ->
        assertEquals(true, entry.feature?.properties?.getRaw("modified"))
        if (entry.id == "00123" || entry.id == ImlTestFixture.INJECTION_ID) null else entry
      })
      resultFilters.add(resultFilter { entry ->
        assertNotNull(entry.tuple)
        calls++
        val modified = FeatureTuple(entry.tupleNumber, entry.tuple)
        modified.feature = checkNotNull(entry.feature).copy<NakshaFeature>(recursive = true).apply {
          properties.setRaw("modified", true)
        }
        modified
      })
    }
    storage.newReadSession().use { session ->
      assertEquals(listOf("far"), ids(session.executeRead(filtered) as SuccessResponse))
      assertEquals(3, calls)
      calls = 0
      filtered.returnHandle = true
      val first = session.executeRead(filtered) as SuccessResponse
      assertEquals(listOf("far"), ids(first))
      assertEquals(5, calls)
      filtered.resultFilters.clear()
      fixture.connection.createStatement().use { it.executeUpdate("DELETE FROM ${fixture.sqlTable}") }
      Naksha.cache.clear(storage)
      val next = session.executeRead(ReadHandle(first.getRaw("handle") as String)) as SuccessResponse
      assertEquals(listOf("null-geo"), ids(next))
      assertEquals(true, next.features[0]?.properties?.getRaw("modified"))
      assertEquals("keep-me", next.features[0]?.properties?.xyz?.getRaw("unrecognized"))
    }
  }

  @Test
  fun `nonpaged filters stop scanning at hard cap and refill across batch boundaries`() = ImlTestFixture.withFixture { fixture ->
    fixture.config.hardCap = 150
    for (i in 1..160) fixture.insert("extra-$i", 20, "I", "test", null, 1000L + i)
    fixture.storage.newReadSession().use { session ->
      var calls = 0
      val response = session.executeRead(request(fixture).apply {
        limit = 2
        resultFilters.add(resultFilter { entry -> calls++; if (calls > 128) entry else null })
      }) as SuccessResponse
      assertEquals(2, response.length)
      assertEquals(130, calls)
      calls = 0
      val empty = session.executeRead(request(fixture).apply {
        limit = 1
        resultFilters.add(resultFilter { calls++; null })
      }) as SuccessResponse
      assertEquals(0, empty.length)
      assertEquals(150, calls)
      assertNull(empty.getRaw("handle") as? String)
    }
  }

  @Test
  fun `legacy properties work via helper and direct field and reject mixed modern queries`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val property = PQuery(Property("properties", "custom"), StringOp.EQUALS, "kept")
      for (helper in listOf(false, true)) {
        val request = request(fixture).apply {
          limit = 2
          if (helper) withPropertyQuery(property) else query.properties = property
        }
        assertEquals(2, session.executeRead(request).length)
        request.query.properties = PQuery(Property("id"), StringOp.EQUALS, "unknown")
        assertEquals(listOf("unknown"), ids(session.executeRead(request) as SuccessResponse))
        request.queryMembers = Equals("id", "unknown")
        illegal { session.executeRead(request) }
      }
    }
  }

  @Test
  fun `metadata supports filtered paging without changing collection feature type`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      val request = ReadCollections().withMapId(fixture.schema).apply {
        limit = 1
        returnHandle = true
        resultFilters.add(resultFilter { entry ->
          assertEquals(NakshaCollection.FEATURE_TYPE, entry.feature?.featureType)
          if (entry.id == Id.COLLECTIONS_COL_TEXT) null else entry
        })
      }
      val first = session.executeRead(request) as SuccessResponse
      assertEquals(1, first.length)
      val second = session.executeRead(ReadHandle(first.getRaw("handle") as String)) as SuccessResponse
      assertEquals(1, second.length)
      assertEquals(NakshaCollection.FEATURE_TYPE, second.features[0]?.featureType)
      assertNull(second.getRaw("handle") as? String)
    }
  }

  @Test
  fun `nonpaged reads do not reserve snapshot capacity`() = ImlTestFixture.withFixture { fixture ->
    fixture.storage.newReadSession().use { session ->
      repeat(128) { session.executeRead(request(fixture).apply { returnHandle = true; limit = 1 }) }
      assertEquals(5, session.executeRead(request(fixture).apply { returnHandle = false }).length)
      assertEquals(0, session.executeRead(request(fixture).apply {
        returnHandle = true
        limit = 1
        resultFilters.add(resultFilter { null })
      }).length)
      assertEquals(1, session.executeRead(request(fixture).apply {
        returnHandle = true
        limit = 1
        resultFilters.add(resultFilter { if (it.id == "far") it else null })
      }).length)
      illegal { session.executeRead(request(fixture).apply { returnHandle = true; limit = 1 }) }
    }
  }

  @Test
  fun `byte budgets release after consumption expiration and clear and do not charge empty results`() {
    val options = SessionOptions(appId = "owner")
    val tuple = ImlTuple.encode(NakshaFeature("body").apply {
      properties.setRaw("payload", "x".repeat(1024))
    }, TupleNumber(1, 1, 1, 1, 4))
    val bytes = tuple.featureBytes.size.toLong() * 4 + 512
    val tuples = listOf(tuple, tuple)
    var now = 0L
    val handles = ImlHandles(maxBytes = bytes * 4, maxSnapshotBytes = bytes * 2, ttlMillis = 100000) { now }
    assertEquals(bytes, handles.addBytes(0, tuple))
    assertEquals(bytes * 2, handles.addBytes(bytes, tuple))
    illegal { handles.addBytes(bytes * 2, tuple) }
    illegal { handles.create(listOf(tuple, tuple, tuple), 1, options) }
    val first = handles.create(tuples, 1, options)
    handles.create(tuples, 1, options)
    illegal { handles.create(tuples, 1, options) }
    assertEquals(0, handles.create(emptyList(), 1, options).length)
    assertEquals(2, handles.retainedSnapshots)
    val consumed = handles.read(ReadHandle(first.getRaw("handle") as String), options, 100)
    assertNull(consumed.getRaw("handle") as? String)
    assertEquals(1, handles.retainedSnapshots)
    handles.create(tuples, 1, options)
    now = 100000
    handles.expire()
    assertEquals(0, handles.retainedSnapshots)
    handles.create(tuples, 1, options)
    handles.clear()
    assertEquals(0, handles.retainedSnapshots)
    handles.create(tuples, 1, options)
    handles.clear()
  }

  @Test
  fun `idle snapshots expire without another request`() {
    val handles = ImlHandles(ttlMillis = 30)
    val tuple = ImlTuple.encode(NakshaFeature("idle"), TupleNumber(1, 1, 1, 1, 4))
    handles.create(listOf(tuple, tuple), 1, SessionOptions(appId = "owner"))
    val deadline = System.nanoTime() + 2000000000L
    while (handles.retainedSnapshots != 0 && System.nanoTime() < deadline) Thread.sleep(10)
    assertEquals(0, handles.retainedSnapshots)
    handles.clear()
  }

  @Test
  fun `continuations require explicit returnHandle and single use frees the snapshot`() {
    val handles = ImlHandles(maxSnapshots = 1)
    val options = SessionOptions(appId = "owner")
    val tuple = ImlTuple.encode(NakshaFeature("feature"), TupleNumber(1, 1, 1, 1, 4))
    val tuples = listOf(tuple, tuple, tuple, tuple)
    for (flag in listOf<Boolean?>(null, false, true)) {
      val first = handles.create(tuples, 1, options)
      val token = first.getRaw("handle") as String
      val second = handles.read(ReadHandle(token).apply { returnHandle = flag }, options, 100)
      assertEquals(1, second.length)
      assertEquals(flag == true, second.getRaw("handle") is String)
      illegal { handles.read(ReadHandle(token), options, 100) }
      if (flag == true) {
        val third = handles.read(ReadHandle(second.getRaw("handle") as String).apply { returnHandle = false }, options, 100)
        assertNull(third.getRaw("handle") as? String)
      }
      assertEquals(0, handles.retainedSnapshots)
    }
  }

  @Test
  fun `legacy property validation rejects cycles depth node excess and null children allowing reused branches`() {
    val leaf = PQuery(Property("properties", "custom"), StringOp.EQUALS, "kept")
    ImlReadSession.validateProperties(PAnd(POr(leaf, leaf), PNot(leaf)))
    val cycle = PNot(leaf)
    cycle.query = cycle
    illegal { ImlReadSession.validateProperties(cycle) }
    val andCycle = PAnd().apply { add(this) }
    illegal { ImlReadSession.validateProperties(andCycle) }
    val orCycle = POr().apply { add(this) }
    illegal { ImlReadSession.validateProperties(orCycle) }
    var deep: IPropertyQuery = leaf
    repeat(66) { deep = PNot(deep) }
    illegal { ImlReadSession.validateProperties(deep) }
    val many = PAnd().apply { repeat(1025) { add(leaf) } }
    illegal { ImlReadSession.validateProperties(many) }
    val nullChild = POr().apply { add(null) }
    illegal { ImlReadSession.validateProperties(nullChild) }
  }

  private fun request(fixture: ImlTestFixture) = ReadFeatures().apply {
    catalogId = fixture.schema
    collectionId = fixture.layer
  }

  private fun ids(response: SuccessResponse): List<String?> = response.features.map { it?.id }

  private fun resultFilter(block: (FeatureTuple) -> FeatureTuple?) = object : ResultFilter {
    override fun filter(featureTuple: FeatureTuple): FeatureTuple? = block(featureTuple)
  }

  private fun illegal(block: () -> Unit) {
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, assertThrows(NakshaException::class.java, block).error.code)
  }
}
