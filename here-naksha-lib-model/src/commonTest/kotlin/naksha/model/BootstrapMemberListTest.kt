package naksha.model

import naksha.model.objects.MemberList
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BootstrapMemberListTest {
  @Test
  fun injectsAllMissingMandatoryMembersAndCanVerifyAgain() {
    val members = MemberList()
    members.verify()
    assertEquals(StandardMembers.MANDATORY.size, members.size)
    assertTrue(members.isSortedByIndex())
    assertSame(members, members.verify())
  }

  @Test
  fun acceptsFirstMandatoryAndInjectsRemainingMembers() {
    val members = MemberList(StandardMembers.MANDATORY[0].copy<Member>())
    members.verify()
    assertEquals(StandardMembers.MANDATORY.size, members.size)
    assertTrue(members.isSortedByIndex())
    for (i in StandardMembers.MANDATORY.indices) {
      val mandatory = StandardMembers.MANDATORY[i]
      assertTrue(mandatory.isSameAs(members[i]))
      assertEquals(i, members[mandatory.name]?.index)
    }
    members.verify()
    assertTrue(members.isSortedByIndex())
  }

  @Test
  fun reordersMembersWithoutTransientDuplicateFailures() {
    val members = MemberList()
    for (i in StandardMembers.MANDATORY.indices.reversed()) members.add(StandardMembers.MANDATORY[i].copy<Member>())
    members.add(Member("small", naksha.model.objects.MemberType.INT8))
    members.add(Member("wide", naksha.model.objects.MemberType.INT64))
    members.verify()
    for (i in StandardMembers.MANDATORY.indices) assertTrue(StandardMembers.MANDATORY[i].isSameAs(members[i]))
    assertEquals("wide", members[StandardMembers.MANDATORY.size]?.name)
    assertEquals("small", members[StandardMembers.MANDATORY.size + 1]?.name)
    for (i in members.indices) assertEquals(i, members[members[i]!!.name]?.index)
    assertSame(members, members.verify())
  }

  @Test
  fun normalizesEqualAlignmentMembersByNameRegardlessOfDeclarationOrder() {
    val declarations = arrayOf(
      arrayOf("zulu", "alpha", "middle"), arrayOf("zulu", "middle", "alpha"),
      arrayOf("alpha", "zulu", "middle"), arrayOf("alpha", "middle", "zulu"),
      arrayOf("middle", "alpha", "zulu"), arrayOf("middle", "zulu", "alpha")
    )
    for (names in declarations) {
      val members = MemberList(Member("aardvark", MemberType.INT8), Member("wide", MemberType.INT64))
      for (name in names) members.add(Member(name, MemberType.INT32))
      members.verify()
      val expected = StandardMembers.MANDATORY.map { it.name } + listOf("wide", "alpha", "middle", "zulu", "aardvark")
      assertEquals(expected, members.map { it!!.name })
      for (i in members.indices) assertEquals(i, members[members[i]!!.name]?.index)
      assertSame(members, members.verify())
      assertEquals(expected, members.map { it!!.name })
    }
  }

  @Test
  fun sequentialStoredIndicesDoNotBypassCanonicalOrdering() {
    val members = MemberList()
    for (mandatory in StandardMembers.MANDATORY) members.add(mandatory.copy<Member>())
    members.add(Member("zulu", MemberType.INT32))
    members.add(Member("alpha", MemberType.INT32))
    for (i in members.indices) members[i]!!.setRaw("index", i)
    assertTrue(members.isSortedByIndex())
    members.verify()
    assertEquals("alpha", members[StandardMembers.MANDATORY.size]?.name)
    assertEquals("zulu", members[StandardMembers.MANDATORY.size + 1]?.name)
    for (i in members.indices) assertEquals(i, members[members[i]!!.name]?.index)
  }
}
