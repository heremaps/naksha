package naksha.model

import naksha.base.NakshaException
import naksha.base.Platform
import naksha.model.objects.Index
import naksha.model.objects.NakshaCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class IndexIdTest {
    @Test
    fun supportsWideLogicalNamesAndCachesWithoutSerialization() {
        for (name in listOf("Road Name", "road-west", "caf\u00e9", "a".repeat(100))) {
            val index = Index(name, "name")
            val before = Platform.toJSON(index)
            val id = index.id
            assertEquals(name, id.text)
            assertSame(id, index.id)
            assertEquals(before, Platform.toJSON(index))
            assertFalse(index.hasRaw("id"))
            assertFalse(index.hasRaw("_id_org"))
            assertFalse(index.hasRaw("_id_val"))
            NakshaCollection("collection").addIndex(index)
            index.name = "renamed"
            assertNotSame(id, index.id)
            val renamed = index.id
            index.setRaw("name", "raw replacement")
            assertNotSame(renamed, index.id)
            assertEquals("raw replacement", index.id.text)
        }
    }

    @Test
    fun rejectsIllegalAndReservedCustomNamesEvenThroughRawMaps() {
        for (name in listOf("", "a'b", "a\"b", "a\n", "i\$1", "naksha~admin")) {
            assertFailsWith<NakshaException> { Index(name, "name") }
            val raw = Index()
            raw.setRaw("name", name)
            assertFailsWith<NakshaException> { raw.id }
            assertFailsWith<NakshaException> { NakshaCollection("collection").addIndex(raw) }
        }
    }
}
