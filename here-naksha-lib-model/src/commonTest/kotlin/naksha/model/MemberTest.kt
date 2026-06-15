package naksha.model

import naksha.model.objects.Index
import naksha.model.objects.IndexType
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
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
        assertEquals(listOf("properties", "age"), m.effectivePath())
    }

    @Test
    fun effectivePathUsesExplicitMap() {
        val m = Member("price", MemberType.FLOAT64, JsonPath("properties", "sale", "price"))
        assertEquals(listOf("properties", "sale", "price"), m.effectivePath())
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
        c.addIndex(Index("idx1", IndexType.BTREE, "id"))
        assertFailsWith<NakshaException> {
            c.addIndex(Index("idx1", IndexType.SPATIAL, "geo"))
        }
    }

    @Test
    fun indexTypesExist() {
        assertNotNull(IndexType.BTREE)
        assertNotNull(IndexType.SPATIAL)
        assertNotNull(IndexType.TAGS)
        assertNotNull(IndexType.SET)
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
        assertNotNull(MemberType.TAGS)
        assertNotNull(MemberType.TAGS_FROM_ARRAY)
        assertNotNull(MemberType.SET)
    }

    @Test
    fun standardTagsMemberDefaultsToSet() {
        assertEquals(MemberType.SET, naksha.model.objects.StandardMembers.XyzTags.dataType)
        assertEquals(IndexType.SET, naksha.model.objects.XyzIndices.XyzTags.type)
    }

    @Test
    fun tagListEncodesAsJsonArrayPreservingOrder() {
        val tags = TagList("foo", "bar", "a=b")
        val json = Naksha.encodeTagList(tags)
        assertNotNull(json)
        // Round-trip must preserve the exact element order (set guarantee).
        val decoded = Naksha.decodeTagList(json)
        assertNotNull(decoded)
        assertEquals(listOf("foo", "bar", "a=b"), decoded.filterNotNull())
    }

    @Test
    fun decodeTagListSupportsLegacyMapForm() {
        // TAGS_FROM_ARRAY persists a JSON object; decodeTagList must re-flatten it.
        val decoded = Naksha.decodeTagList("""{"foo":null,"a":"b","n":5.0}""")
        assertNotNull(decoded)
        val elements = decoded.filterNotNull().toSet()
        assertEquals(setOf("foo", "a=b", "n:=5.0"), elements)
    }

    @Test
    fun encodeTagListOfEmptyIsNull() {
        assertEquals(null, Naksha.encodeTagList(null))
        assertEquals(null, Naksha.encodeTagList(TagList()))
    }
}
