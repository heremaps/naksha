package naksha.model

import naksha.model.objects.CustomIndex
import naksha.model.objects.CustomIndexType
import naksha.model.objects.CustomMember
import naksha.model.objects.CustomMemberList
import naksha.model.objects.CustomMemberType
import naksha.model.objects.JsonPath
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CustomMemberTest {

    @Test
    fun defaultDataTypeIsString() {
        val m = CustomMember("age")
        assertEquals("age", m.name)
        assertEquals(CustomMemberType.STRING, m.dataType)
    }

    @Test
    fun effectivePathDefaultsToPropertiesName() {
        val m = CustomMember("age", CustomMemberType.INT32)
        assertEquals(listOf("properties", "age"), m.effectivePath())
    }

    @Test
    fun effectivePathUsesExplicitMap() {
        val m = CustomMember("price", CustomMemberType.FLOAT64, JsonPath("properties", "sale", "price"))
        assertEquals(listOf("properties", "sale", "price"), m.effectivePath())
    }

    @Test
    fun addMemberRejectsInvalidName() {
        val c = NakshaCollection("my_coll")
        assertFailsWith<NakshaException> { c.addMember(CustomMember("BadName")) }
        assertFailsWith<NakshaException> { c.addMember(CustomMember("123start")) }
        assertFailsWith<NakshaException> { c.addMember(CustomMember("with space")) }
    }

    @Test
    fun addMemberRejectsDuplicates() {
        val c = NakshaCollection("my_coll")
        c.addMember(CustomMember("age", CustomMemberType.INT32))
        assertFailsWith<NakshaException> { c.addMember(CustomMember("age", CustomMemberType.INT64)) }
    }

    @Test
    fun addMemberStoresInOrder() {
        val c = NakshaCollection("my_coll")
        c.addMember(CustomMember("a", CustomMemberType.INT32))
        c.addMember(CustomMember("b", CustomMemberType.STRING))
        val members = c.members
        assertNotNull(members)
        assertEquals(2, members.size)
        assertEquals("a", members[0]!!.name)
        assertEquals("b", members[1]!!.name)
    }

    @Test
    fun addMemberCapsAt64() {
        val c = NakshaCollection("my_coll")
        for (i in 0 until CustomMemberList.MAX_MEMBERS) {
            c.addMember(CustomMember("m$i", CustomMemberType.INT32))
        }
        assertFailsWith<NakshaException> { c.addMember(CustomMember("over", CustomMemberType.INT32)) }
    }

    @Test
    fun addCustomIndexRejectsDuplicateName() {
        val c = NakshaCollection("my_coll")
        c.addCustomIndex(CustomIndex("idx1", CustomIndexType.BTREE, "id"))
        assertFailsWith<NakshaException> {
            c.addCustomIndex(CustomIndex("idx1", CustomIndexType.SPATIAL, "geo"))
        }
    }

    @Test
    fun customIndexTypesExist() {
        assertNotNull(CustomIndexType.BTREE)
        assertNotNull(CustomIndexType.SPATIAL)
        assertNotNull(CustomIndexType.FLAT_MAP)
    }

    @Test
    fun customMemberTypesCoverPrimitivesAndVirtuals() {
        // Primitives.
        assertNotNull(CustomMemberType.BOOLEAN)
        assertNotNull(CustomMemberType.INT8)
        assertNotNull(CustomMemberType.INT16)
        assertNotNull(CustomMemberType.INT32)
        assertNotNull(CustomMemberType.INT64)
        assertNotNull(CustomMemberType.FLOAT32)
        assertNotNull(CustomMemberType.FLOAT64)
        assertNotNull(CustomMemberType.STRING)
        assertNotNull(CustomMemberType.BYTE_ARRAY)
        // Virtual.
        assertNotNull(CustomMemberType.FLAT_MAP)
        assertNotNull(CustomMemberType.TAGS)
    }
}
