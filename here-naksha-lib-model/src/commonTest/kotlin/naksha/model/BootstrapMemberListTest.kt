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
}
