package naksha.model

import naksha.model.objects.MemberList
import naksha.model.objects.Member
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
}
