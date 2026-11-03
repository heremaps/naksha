package naksha.psql

import naksha.base.Platform
import naksha.model.objects.NakshaCollection
import naksha.model.objects.XyzIndices
import naksha.model.objects.XyzMembers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgCollectionLayoutJsTest {
  @Test
  fun resolvesDefaultsWithIndependentNestedCopiesInJavaScript() {
    val first = NakshaCollection("js_first")
    val second = NakshaCollection("js_second")
    assertNull(first.members)
    assertNull(first.indices)
    PgCollection.resolveLayout(first)
    PgCollection.resolveLayout(second)
    val members = assertNotNull(first.members)
    val otherMembers = assertNotNull(second.members)
    val indices = assertNotNull(first.indices)
    val otherIndices = assertNotNull(second.indices)
    assertEquals(XyzMembers.ALL.size, members.size)
    assertEquals(XyzIndices.ALL.size, indices.size)
    assertTrue(members.isSortedByIndex())
    for (global in XyzMembers.ALL) {
      val member = assertNotNull(members[global.name])
      val other = assertNotNull(otherMembers[global.name])
      assertTrue(global.isSameAs(member, comparePath = true))
      assertNotSame(global.platformObject(), member.platformObject())
      assertNotSame(other.platformObject(), member.platformObject())
      assertNotSame(global.path.platformObject(), member.path.platformObject())
      assertNotSame(other.path.platformObject(), member.path.platformObject())
      val before = Platform.toJSON(global)
      member.path.add("changed")
      assertEquals(before, Platform.toJSON(global))
      assertContentEquals(global.path, other.path)
    }
    for (i in XyzIndices.ALL.indices) {
      val global = XyzIndices.ALL[i]
      val index = assertNotNull(indices[i])
      val other = assertNotNull(otherIndices[i])
      val before = Platform.toJSON(global)
      assertEquals(before, Platform.toJSON(index))
      assertNotSame(global.platformObject(), index.platformObject())
      assertNotSame(other.platformObject(), index.platformObject())
      assertNotSame(global.on.platformObject(), index.on.platformObject())
      assertNotSame(other.on.platformObject(), index.on.platformObject())
      index.on.add("changed")
      assertEquals(before, Platform.toJSON(global))
      assertContentEquals(global.on, other.on)
    }
  }
}
