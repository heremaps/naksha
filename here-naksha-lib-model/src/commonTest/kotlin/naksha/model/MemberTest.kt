package naksha.model

import naksha.base.NakshaException
import naksha.model.objects.Index
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MemberTest {

    @Test
    fun defaultDataTypeIsString() {
        val m = Member("age")
        assertEquals("age", m.name)
        assertEquals(MemberType.STRING, m.dataType)
    }

    @Test
    fun effectivePathDefaultsToPropertiesName() {
        val m = Member("age", MemberType.INT32)
        assertContentEquals(listOf("properties", "age"), m.path)
    }

    @Test
    fun effectivePathUsesExplicitMap() {
        val m = Member("price", MemberType.FLOAT64, JsonPath("properties", "sale", "price"))
        assertContentEquals(listOf("properties", "sale", "price"), m.path)
    }

    @Test
    fun addMemberRejectsInvalidName() {
        val c = NakshaCollection("my_coll")
        assertFailsWith<NakshaException> { c.addMember(Member("BadName")) }
        assertFailsWith<NakshaException> { c.addMember(Member("123start")) }
        assertFailsWith<NakshaException> { c.addMember(Member("with space")) }
    }

    @Test
    fun addMemberRejectsDuplicates() {
        val c = NakshaCollection("my_coll")
        c.addMember(Member("age", MemberType.INT32))
        assertFailsWith<NakshaException> { c.addMember(Member("age", MemberType.INT64)) }
    }

    @Test
    fun addMemberStoresInOrder() {
        val c = NakshaCollection("my_coll")
        c.addMember(Member("a", MemberType.INT32))
        c.addMember(Member("b", MemberType.STRING))
        val members = c.members
        assertNotNull(members)
        assertEquals(2, members.size)
        assertEquals("a", members[0]!!.name)
        assertEquals("b", members[1]!!.name)
    }

    @Test
    fun addMemberCapsAt64() {
        val c = NakshaCollection("my_coll")
        for (i in 0 until MemberList.MAX_MEMBERS) {
            c.addMember(Member("m$i", MemberType.INT32))
        }
        assertFailsWith<NakshaException> { c.addMember(Member("over", MemberType.INT32)) }
    }

    @Test
    fun addIndexRejectsDuplicateName() {
        val c = NakshaCollection("my_coll")
        c.addIndex(Index("idx1", "id"))
        assertFailsWith<NakshaException> {
            c.addIndex(Index("idx1", "geo"))
        }
    }

    @Test
    fun memberTypesCoverPrimitivesAndVirtuals() {
        // Primitives.
        assertNotNull(MemberType.BOOLEAN)
        assertNotNull(MemberType.INT8)
        assertNotNull(MemberType.INT16)
        assertNotNull(MemberType.INT32)
        assertNotNull(MemberType.INT64)
        assertNotNull(MemberType.FLOAT32)
        assertNotNull(MemberType.FLOAT64)
        assertNotNull(MemberType.STRING)
        assertNotNull(MemberType.BYTE_ARRAY)
        // Virtual / jsonb.
        assertNotNull(MemberType.TAG_MAP)
        assertNotNull(MemberType.TAG_MAP_FROM_TAG_LIST)
        assertNotNull(MemberType.TAG_LIST)
    }

    @Test
    fun standardTagsMemberDefaultsToSet() {
        assertEquals(MemberType.TAG_LIST, naksha.model.objects.XyzMembers.XyzTags.dataType)
        assertNotNull(naksha.model.objects.XyzIndices.XyzTags)
    }
}
