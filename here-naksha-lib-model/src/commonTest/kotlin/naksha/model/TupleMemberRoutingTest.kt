package naksha.model

import naksha.base.AnyObject
import naksha.base.NakshaException
import naksha.jbon.HeapBook
import naksha.jbon.BookType
import naksha.jbon.JbEncoder2
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    val routing = TupleMemberRouting(members, book)
    val paths = listOf(arrayOf<Any?>("p"), arrayOf<Any?>("p", "same"), arrayOf<Any?>("array", 0),
      arrayOf<Any?>("array", "0"), arrayOf<Any?>("p", "same", "child"), emptyArray())
    for (path in paths) assertEquals(legacy(members, book, path, path.size), routing.find(path, path.size))
    assertEquals(book.indexOfName("first"), routing.find(arrayOf("p", "same"), 2))
    assertEquals(book.indexOfName("numeric"), routing.find(arrayOf("array", 0), 2))
  }

  @Test
  fun snapshotDoesNotRetainMutablePathsAndNextBuildObservesChanges() {
    val member = Member("value", MemberType.STRING, JsonPath("old"))
    val members = MemberList(member)
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("value", "payload") }
    val before = TupleMemberRouting(members, book)
    member.path[0] = "new"
    assertEquals(0, before.find(arrayOf("old"), 1))
    assertEquals(-1, before.find(arrayOf("new"), 1))
    val after = TupleMemberRouting(members, book)
    assertEquals(-1, after.find(arrayOf("old"), 1))
    assertEquals(0, after.find(arrayOf("new"), 1))
  }

  @Test
  fun missingBookValueFailsOnlyWhenItsPathMatches() {
    val members = MemberList(Member("missing", MemberType.STRING, JsonPath("absent")),
      Member("later", MemberType.STRING, JsonPath("absent")))
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("later", "value") }
    val routing = TupleMemberRouting(members, book)
    assertEquals(-1, routing.find(arrayOf("other"), 1))
    val failure = assertFailsWith<NakshaException> { routing.find(arrayOf("absent"), 1) }
    assertEquals("Member index should not be negative at this point", failure.error.msg)
  }

  @Test
  fun preparedAndLegacyCallbacksEmitIdenticalJbonBytes() {
    val feature = AnyObject().apply {
      setPath("one", "properties", "left")
      setPath("two", "properties", "right")
      setRaw("id", "routing")
    }
    val members = MemberList(Member("left", MemberType.STRING, JsonPath("properties", "left")),
      Member("right", MemberType.STRING, JsonPath("properties", "right")))
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("right", "two"); put("left", "one") }
    val routing = TupleMemberRouting(members, book)
    val expected = JbEncoder2().withMemberEncoder { path, end, _ -> legacy(members, book, path, end) }.buildTupleFromMap(feature)
    val actual = JbEncoder2().withMemberEncoder { path, end, _ -> routing.find(path, end) }.buildTupleFromMap(feature)
    assertContentEquals(expected, actual)
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
