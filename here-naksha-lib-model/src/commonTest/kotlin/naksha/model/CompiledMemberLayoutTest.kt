package naksha.model

import naksha.base.Action
import naksha.base.TupleNumber
import naksha.jbon.MemberValuesBook
import naksha.jbon.HeapBook
import naksha.jbon.BookType
import naksha.base.Platform
import naksha.base.NakshaException
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

class CompiledMemberLayoutTest {
  @Test
  fun relocatedMetadataAndActionClassificationMatchRawEncoding() {
    val members = MemberList(
      Member(StandardMembers.Tn, JsonPath("properties", "nested", "tn")),
      Member("value", MemberType.INT32, JsonPath("properties", "nested", "value")),
    ).verify()
    val layout = CompiledMemberLayout(members)
    for (previous in listOf<TupleNumber?>(null, TupleNumber(1L, 2, 3, 7L, 100L), TupleNumber(9L, 2, 3, 7L, 100L))) {
      for (action in listOf(Action.CREATE, Action.UPDATE, Action.DELETE)) {
        val feature = NakshaFeature("7").apply {
          setPath(123, "properties", "nested", "value")
          if (previous != null) setPath(previous, "properties", "nested", "tn")
        }
        val before = Platform.toJSON(feature)
        val raw = try {
          Tuple.encodeRaw(feature, 1L, 2, 3, members, 104L, requestedAction = action, atomic = true)
        } catch (_: NakshaException) {
          assertFailsWith<NakshaException> { Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 104L, requestedAction = action, atomic = true) }
          continue
        }
        val compiled = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 104L, requestedAction = action, atomic = true)
        assertContentEquals(raw.tuple.featureBytes, compiled.tuple.featureBytes)
        assertEquals(raw.previousTupleNumber, compiled.previousTupleNumber)
        assertEquals(before, Platform.toJSON(feature))
      }
    }
  }

  @Test
  fun malformedMandatoryTypesAreRejectedBeforeCompiledEncoding() {
    for (standard in listOf(StandardMembers.Id, StandardMembers.Tn, StandardMembers.NextVersion, StandardMembers.GlobalBookFeatureNumber)) {
      val members = MemberList().verify()
      val member = members.useMember(standard)
      member.dataType = if (standard.dataType == MemberType.STRING) MemberType.INT32 else MemberType.STRING
      assertFailsWith<NakshaException> {
        Tuple.encodeRaw(NakshaFeature("7"), 1L, 2, 3, members, 100L, requestedAction = Action.CREATE)
      }
      assertFailsWith<NakshaException> { CompiledMemberLayout(members) }
    }
  }

  @Test
  fun optionalOriginRetainsTypeSensitiveRawMatching() {
    val members = MemberList(StandardMembers.Origin.copy<Member>(recursive = true)).verify()
    members.findMember(StandardMembers.Origin)!!.dataType = MemberType.INT32
    val layout = CompiledMemberLayout(members)
    val feature = NakshaFeature("7").apply { setPath(TupleNumber(9L, 2, 3, 7L, 100L), members.useMember(StandardMembers.Tn).path) }
    val raw = Tuple.encodeRaw(feature, 1L, 2, 3, members, 104L, requestedAction = Action.CREATE).tuple
    val compiled = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 104L, requestedAction = Action.CREATE).tuple
    assertContentEquals(raw.featureBytes, compiled.featureBytes)
    assertEquals(raw.membersBook[StandardMembers.ORIGIN], compiled.membersBook[StandardMembers.ORIGIN])
  }

  @Test
  fun virtualOriginIsPreparedButNotStoredInTheBook() {
    val origin = StandardMembers.Origin.copy<Member>(recursive = true).apply { setRaw("virtual", true) }
    val members = MemberList(origin).verify()
    val layout = CompiledMemberLayout(members)
    val previous = TupleNumber(9L, 2, 3, 7L, 100L)
    val feature = NakshaFeature("7").apply { setPath(previous, members.useMember(StandardMembers.Tn).path) }
    val raw = Tuple.encodeRaw(feature, 1L, 2, 3, members, 104L, requestedAction = Action.CREATE).tuple
    val compiled = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 104L, requestedAction = Action.CREATE).tuple
    assertContentEquals(raw.featureBytes, compiled.featureBytes)
    assertEquals(-1, compiled.membersBook.indexOfName(StandardMembers.ORIGIN))
    assertEquals(previous.toString(), compiled.decodeObject(null).getPath(origin.path))
  }

  @Test
  fun processorsGlobalBookAndAbsentPathsPreserveBytesAndInputs() {
    val members = MemberList(Member("label", MemberType.STRING, JsonPath("properties", "label"))).verify()
    val layout = CompiledMemberLayout(members)
    val processors = MemberProcessorMap().addProcessor("label", IMemberProcessor { _, list, _, member, _ ->
      assertEquals("label", member.name)
      assertEquals(members.size, list.size)
      "processed"
    })
    val global = HeapBook(BookType.GLOBAL_BOOK).apply {
      databaseNumber = 1L
      featureNumber = 4L
      put("alpha", "alpha")
      put("beta", "beta")
    }
    val feature = NakshaFeature("5").apply { properties.setRaw("extra", "alpha beta") }
    val before = Platform.toJSON(feature)
    val raw = Tuple.encodeRaw(feature, 1L, 2, 3, members, 100L, globalBook = global, processors = processors, requestedAction = Action.CREATE).tuple
    val compiled = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 100L, globalBook = global, processors = processors, requestedAction = Action.CREATE).tuple
    assertContentEquals(raw.featureBytes, compiled.featureBytes)
    assertEquals("processed", compiled.decodeObject(global).getPath("properties", "label"))
    assertEquals("alpha beta", compiled.decodeObject(global).getPath("properties", "extra"))
    assertEquals(before, Platform.toJSON(feature))
  }

  @Test
  fun compiledAndMutablePathsProduceIdenticalBytesAndIndependentValues() {
    val member = Member("value", MemberType.INT32, JsonPath("properties", "value"))
    val members = MemberList(member).verify()
    val layout = CompiledMemberLayout(members)
    val feature = NakshaFeature("7").apply { properties.setRaw("value", 123); properties.setRaw("undeclared", "remainder") }
    val raw = Tuple.encodeRaw(feature, 1L, 2, 3, members, 100L, requestedAction = Action.CREATE).tuple
    val compiled = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 100L, requestedAction = Action.CREATE).tuple
    assertContentEquals(raw.featureBytes, compiled.featureBytes)
    for (i in 0 until raw.membersBook.length) {
      assertEquals(raw.membersBook.getNameAt(i), compiled.membersBook.getNameAt(i))
      assertEquals(raw.membersBook[i], compiled.membersBook[i])
    }
    assertIs<MemberValuesBook>(compiled.membersBook)
    assertSame(layout.bookLayout, compiled.membersBook.layout)
    assertEquals("remainder", compiled.decodeObject(null).getPath("properties", "undeclared"))
    feature.properties.setRaw("value", 456)
    val next = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 104L, requestedAction = Action.CREATE).tuple
    assertEquals(123, compiled.membersBook["value"])
    assertEquals(456, next.membersBook["value"])
    assertNull(feature.tupleNumber)
    assertEquals(-1, layout.bookLayout.indexOfName(StandardMembers.FeatureBytes.name))
  }

  @Test
  fun snapshotsIgnoreSourceMutationAndMetadataUpdatesInvalidateSuccessorCache() {
    val member = Member("value", MemberType.INT32, JsonPath("properties", "value"))
    val members = MemberList(member).verify()
    val layout = CompiledMemberLayout(members)
    member.path[1] = "changed"
    member.dataType = MemberType.STRING
    member.setRaw("name", "renamed")
    val feature = NakshaFeature("8").apply { properties.setRaw("value", 123); properties.setRaw("changed", "string") }
    val tuple = Tuple.encodeWithLayout(feature, 1L, 2, 3, layout, 100L, requestedAction = Action.CREATE).tuple
    assertEquals(123, tuple.membersBook["value"])
    assertEquals(-1, tuple.membersBook.indexOfName("renamed"))
    assertEquals(123, tuple.decodeObject(null).getPath("properties", "value"))
    val replacement = CompiledMemberLayout(members)
    val changed = Tuple.encodeWithLayout(feature, 1L, 2, 3, replacement, 104L, requestedAction = Action.CREATE).tuple
    assertEquals("string", changed.membersBook["renamed"])
    tuple.nextVersion = 108L
    assertEquals(TupleNumber.copy(tuple.tupleNumber, version = 108L), tuple.nextTupleNumber)
    tuple.nextVersion = 112L
    assertEquals(TupleNumber.copy(tuple.tupleNumber, version = 112L), tuple.nextTupleNumber)
    val copied = MemberValuesBook.copyOf(tuple.membersBook)
    copied.setNamedValue(StandardMembers.NEXT_VERSION, 116L)
    assertEquals(112L, tuple.nextVersion)
  }
}
