package naksha.model

import naksha.base.IdTextType
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.model.objects.Member
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MemberIdTest {
    @Test
    fun acceptsWideLogicalNamesWithoutChangingBookKeysOrPaths() {
        for (name in listOf("BadName", "123start", "with space", "road-west", "road~old", "caf\u00e9", "a".repeat(100))) {
            val member = Member(name)
            assertEquals(name, member.name)
            assertEquals(name, member.id.text)
            assertContentEquals(listOf("properties", name), member.path)
            val collection = NakshaCollection("my_coll")
            collection.addMember(member)
            assertSame(member, collection.members!![0])
        }
    }

    @Test
    fun cachesIdByNameIdentityAndKeepsCacheOutOfSerialization() {
        val member = Member("first")
        val before = Platform.toJSON(member)
        val id = member.id
        assertSame(id, member.id)
        assertEquals(before, Platform.toJSON(member))
        assertFalse(member.hasRaw("id"))
        assertFalse(member.hasRaw("_id"))
        assertFalse(member.hasRaw("_name"))
        member.name = "second"
        assertNotSame(id, member.id)
        assertEquals("second", member.id.text)
        val second = member.id
        member.setRaw("name", "third")
        assertNotSame(second, member.id)
        assertEquals("third", member.id.text)
    }

    @Test
    fun rejectsInvalidNamesThroughSettersAndRawMapValidation() {
        val member = Member("valid")
        for (name in listOf("", "a'b", "a\"b", "a\n", "a\ud800", "m\$1", "naksha~admin")) {
            assertFailsWith<NakshaException> { member.name = name }
            assertEquals("valid", member.name)
            val raw = Member()
            raw.setRaw("name", name)
            assertFailsWith<NakshaException> { raw.id }
            assertFailsWith<NakshaException> { NakshaCollection("my_coll").addMember(raw) }
        }
        assertEquals(IdTextType.SAFE, Member("valid_name").id.textType)
    }
}
