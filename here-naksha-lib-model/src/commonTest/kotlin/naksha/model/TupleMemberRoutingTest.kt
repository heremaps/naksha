package naksha.model

import naksha.base.AnyObject
import naksha.base.NakshaException
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set
import naksha.jbon.HeapBook
import naksha.jbon.BookType
import naksha.jbon.JbEncoder2
import naksha.jbon.JbMemberReferenceBuilder
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertNotSame

class TupleMemberRoutingTest {
  @Test
  fun exactRoutesKeepFirstMatchNumericSegmentsAndActualBookPositions() {
    val ignored = Member("ignored", MemberType.STRING, JsonPath("p", "same")).apply { setRaw("virtual", true) }
    val first = Member("first", MemberType.STRING, JsonPath("p", "same"))
    val later = Member("later", MemberType.STRING, JsonPath("p", "same"))
    val prefix = Member("prefix", MemberType.TAG_MAP, JsonPath("p"))
    val numeric = Member("numeric", MemberType.STRING, JsonPath("array", 0))
    val textual = Member("textual", MemberType.STRING, JsonPath("array", "0"))
    val members = MemberList(ignored, first, later, prefix, numeric, textual)
    val book = HeapBook(BookType.MEMBER_BOOK)
    for (name in listOf("textual", "later", "numeric", "first", "prefix")) book.put(name, name)
    val routing = members.memberReferencePlan(book)
    val paths = listOf(arrayOf<Any?>("p"), arrayOf<Any?>("p", "same"), arrayOf<Any?>("array", 0),
      arrayOf<Any?>("array", "0"), arrayOf<Any?>("p", "same", "child"), emptyArray())
    for (path in paths) assertEquals(legacy(members, book, path, path.size), routing.memberIndex(path))
    assertEquals(book.indexOfName("first"), routing.memberIndex(arrayOf("p", "same")))
    assertEquals(book.indexOfName("numeric"), routing.memberIndex(arrayOf("array", 0)))
  }

  @Test
  fun snapshotDoesNotRetainMutablePathsAndNextBuildObservesChanges() {
    val member = Member("value", MemberType.STRING, JsonPath("old"))
    val members = MemberList(member)
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("value", "payload") }
    val before = members.memberReferencePlan(book)
    member.path[0] = "new"
    assertEquals(0, before.memberIndex(arrayOf("old")))
    assertEquals(-1, before.memberIndex(arrayOf("new")))
    val after = members.memberReferencePlan(book)
    assertEquals(-1, after.memberIndex(arrayOf("old")))
    assertEquals(0, after.memberIndex(arrayOf("new")))
  }

  @Test
  fun missingBookValueFailsOnlyWhenItsPathMatches() {
    val members = MemberList(Member("missing", MemberType.STRING, JsonPath("absent")),
      Member("later", MemberType.STRING, JsonPath("absent")))
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("later", "value") }
    val routing = members.memberReferencePlan(book)
    assertEquals(-1, routing.memberIndex(arrayOf("other")))
    val failure = assertFailsWith<NakshaException> { routing.memberIndex(arrayOf("absent")) }
    assertEquals("Member index should not be negative at this point", failure.error.msg)
  }

  @Test
  fun reusedAndFreshPlansEmitIdenticalJbonBytes() {
    val feature = AnyObject().apply {
      setPath("one", "properties", "left")
      setPath("two", "properties", "right")
      setRaw("id", "routing")
    }
    val members = MemberList(Member("left", MemberType.STRING, JsonPath("properties", "left")),
      Member("right", MemberType.STRING, JsonPath("properties", "right")))
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("right", "two"); put("left", "one") }
    val routing = members.memberReferencePlan(book)
    val fresh = JbMemberReferenceBuilder().add(arrayOf("properties", "left"), book.indexOfName("left"))
      .add(arrayOf("properties", "right"), book.indexOfName("right")).build()
    val expected = JbEncoder2().withMemberReferences(fresh).buildTupleFromMap(feature)
    val actual = JbEncoder2().withMemberReferences(routing).buildTupleFromMap(feature)
    assertContentEquals(expected, actual)
  }

  @Test
  fun layoutReuseChecksActualBookPositionsAndDoesNotCacheValues() {
    val members = MemberList(Member("a", MemberType.STRING, JsonPath("a")), Member("b", MemberType.STRING, JsonPath("b")))
    val firstBook = HeapBook(BookType.MEMBER_BOOK).apply { put("a", "first"); put("b", "second") }
    val first = members.memberReferencePlan(firstBook)
    val serialized = naksha.base.Platform.toJSON(members)
    firstBook.put("a", "changed")
    assertSame(first, members.memberReferencePlan(firstBook))
    val reordered = HeapBook(BookType.MEMBER_BOOK).apply { put("b", "second"); put("a", "changed") }
    val next = members.memberReferencePlan(reordered)
    assertNotSame(first, next)
    assertEquals(0, first.memberIndex(arrayOf("a")))
    assertEquals(1, next.memberIndex(arrayOf("a")))
    assertEquals("changed", reordered[next.memberIndex(arrayOf("a"))])
    assertEquals(serialized, naksha.base.Platform.toJSON(members), "Reference caches must not enter serialized declarations")
  }

  @Test
  fun layoutReuseObservesRawNamesVirtualFlagsAndDeclarationOrder() {
    val a = Member("a", MemberType.STRING, JsonPath("a"))
    val b = Member("b", MemberType.STRING, JsonPath("b"))
    val members = MemberList(a, b)
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("a", "a"); put("b", "b"); put("renamed", "new") }
    val original = members.memberReferencePlan(book)
    a.setRaw("name", "renamed")
    val renamed = members.memberReferencePlan(book)
    assertNotSame(original, renamed)
    assertEquals(book.indexOfName("renamed"), renamed.memberIndex(arrayOf("a")))
    a.setRaw("virtual", true)
    val virtual = members.memberReferencePlan(book)
    assertEquals(-1, virtual.memberIndex(arrayOf("a")))
    a.setRaw("virtual", false)
    val restored = members.memberReferencePlan(book)
    assertEquals(book.indexOfName("renamed"), restored.memberIndex(arrayOf("a")))
    array_set(members.platformObject(), 0, b.platformObject())
    array_set(members.platformObject(), 1, a.platformObject())
    val reordered = members.memberReferencePlan(book)
    assertNotSame(restored, reordered)
    array_set(members.platformObject(), 0, Member("b", MemberType.STRING, JsonPath("b")).platformObject())
    assertSame(reordered, members.memberReferencePlan(book), "Equivalent content, not object identity, permits reuse")
  }

  private fun legacy(members: MemberList, book: HeapBook, path: Array<Any?>, end: Int): Int {
    members@ for (i in 0 until members.size) {
      val member = members[i] ?: continue
      if (member.isVirtual()) continue
      val declared = member.path
      if (declared.size != end) continue
      for (j in 0 until end) if (path[j] != declared[j]) continue@members
      return book.indexOfName(member.name)
    }
    return -1
  }
}
