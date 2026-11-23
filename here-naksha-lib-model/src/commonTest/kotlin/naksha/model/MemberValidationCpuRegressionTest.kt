package naksha.model

import naksha.base.NakshaException
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_push
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MemberValidationCpuRegressionTest {
  @Test
  fun validListsRemainValidatedAfterInPlaceNameChanges() {
    val first = Member("first")
    val second = Member("second")
    val members = MemberList(first, second)
    val collection = NakshaCollection("validation", "catalog").withMembers(members).withIndices(IndexList())
    val validated = collection.validatedMembers
    assertSame(collection.members, validated)
    first.setRaw("name", "second")
    val failure = assertFailsWith<NakshaException> { collection.validatedMembers }
    assertEquals("Member at index 0 has same name as member at 1: second", failure.error.msg)
    first.setRaw("name", "first")
    assertSame(validated, collection.validatedMembers)
  }

  @Test
  fun malformedListsKeepLegacyNullAndDuplicateDiagnosticOrder() {
    // The earliest duplicate's first declaration wins, not the first repeat encountered by a single-pass set. Nulls still interrupt the original inner scan where appropriate.
    val inputs = listOf(
      listOf("a", "b", "b", "a"), listOf("a", "a", null), listOf("a", null, "a"),
      listOf(null, "a", "a"), listOf("a", "b", null, "b", "a"), listOf("a", "b", "a", "b")
    )
    for (input in inputs) {
      val members = MemberList()
      for (name in input) array_push(members.platformObject(), if (name == null) null else Member(name).platformObject())
      val expected = assertFailsWith<NakshaException> { legacyValidation(members) }.error
      val actual = assertFailsWith<NakshaException> { members.validateAndSort() }.error
      assertEquals(expected.code, actual.code, input.toString())
      assertEquals(expected.msg, actual.msg, input.toString())
    }
  }

  private fun legacyValidation(members: MemberList) {
    for (i in 0 until members.size) {
      val member = members[i] ?: throw naksha.base.illegalState("Member at index $i is null")
      val name = member.name
      for (j in i + 1 until members.size) {
        val later = members[j] ?: throw naksha.base.illegalState("Member at index $j is null")
        if (name == later.name) throw naksha.base.illegalState("Member at index $i has same name as member at $j: $name")
      }
    }
  }
}
