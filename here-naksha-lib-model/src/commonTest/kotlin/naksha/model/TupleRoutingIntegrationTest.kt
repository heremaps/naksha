package naksha.model

import naksha.base.Action
import naksha.base.Id
import naksha.base.Platform
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TupleRoutingIntegrationTest {
  @Test
  fun fullEncodingPreservesProcessorValuesAndGlobalDictionaryReferences() {
    // Numeric IDs keep this codec test independent of Node's unsupported MD5 provider; real PostgreSQL/JVM tests cover hashed identities.
    val database = Id("1")
    val catalog = Id("2")
    val collection = Id("3")
    val members = MemberList(Member("label", MemberType.STRING, JsonPath("properties", "label"))).verify()
    val processors = MemberProcessorMap().addProcessor("label", IMemberProcessor { _, _, _, _, _ -> "processed" })
    val global = HeapBook(BookType.GLOBAL_BOOK).apply {
      databaseNumber = database.number
      featureNumber = Id("4").number
      put("alpha", "alpha")
      put("beta", "beta")
    }
    val feature = NakshaFeature("5").apply {
      properties.setRaw("label", "original")
      properties.setRaw("extra", "alpha beta")
    }
    val original = Platform.toJSON(feature)
    val tuple = Tuple.encodeRaw(feature, database.number, catalog.intValue, collection.intValue, members, 100L,
      globalBook = global, processors = processors, requestedAction = Action.CREATE).tuple
    val decoded = tuple.decodeObject(global)
    assertEquals("processed", decoded.getPath("properties", "label"))
    assertEquals("alpha beta", decoded.getPath("properties", "extra"))
    assertEquals("processed", tuple.membersBook["label"])
    assertEquals(original, Platform.toJSON(feature), "The private routing snapshot must not change feature input")
  }

  @Test
  fun consecutiveEncodingsObserveRawPathChangesWithoutStaleRoutes() {
    val member = Member("label", MemberType.STRING, JsonPath("properties", "old"))
    val members = MemberList(member).verify()
    val feature = NakshaFeature("5").apply {
      properties.setRaw("old", "before")
      properties.setRaw("new", "after")
    }
    val database = Id("1")
    val catalog = Id("2")
    val collection = Id("3")
    val first = Tuple.encodeRaw(feature, database.number, catalog.intValue, collection.intValue, members, 100L,
      requestedAction = Action.CREATE).tuple
    assertEquals("before", first.membersBook["label"])
    member.path[1] = "new"
    val second = Tuple.encodeRaw(feature, database.number, catalog.intValue, collection.intValue, members, 104L,
      requestedAction = Action.CREATE).tuple
    assertEquals("after", second.membersBook["label"])
    assertEquals("before", second.decodeObject(null).getPath("properties", "old"))
    assertEquals("after", second.decodeObject(null).getPath("properties", "new"))
  }

  @Test
  fun reusedReferencePlansDoNotCacheMemberCoercionTypes() {
    val member = Member("value", MemberType.STRING, JsonPath("properties", "value"))
    val members = MemberList(member).verify()
    val feature = NakshaFeature("5").apply { properties.setRaw("value", "original") }
    val first = Tuple.encodeRaw(feature, 1L, 2, 3, members, 100L, requestedAction = Action.CREATE).tuple
    val plan = members.memberReferencePlan(first.membersBook)
    assertEquals("original", first.decodeObject(null).getPath("properties", "value"))
    member.dataType = MemberType.INT32
    feature.properties.setRaw("value", 123)
    val second = Tuple.encodeRaw(feature, 1L, 2, 3, members, 104L, requestedAction = Action.CREATE).tuple
    assertSame(plan, members.memberReferencePlan(second.membersBook))
    assertEquals(123, second.decodeObject(null).getPath("properties", "value"))
  }
}
