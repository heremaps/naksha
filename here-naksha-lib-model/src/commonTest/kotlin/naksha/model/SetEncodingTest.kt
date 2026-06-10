package naksha.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies the wire form of [naksha.model.objects.MemberType.SET]:
 * - JSON array of unique strings.
 * - Insertion order preserved at write time.
 * - Duplicate entries dropped at write time.
 * - `null`/empty inputs encode to `null` (no row stored).
 */
class SetEncodingTest {

    @Test
    fun encodeSetPreservesInsertionOrder() {
        val json = Naksha.encodeSet(TagList("zebra", "apple", "mango"))
        assertEquals("""["zebra","apple","mango"]""", json)
    }

    @Test
    fun encodeSetDropsDuplicates() {
        val json = Naksha.encodeSet(TagList("foo", "bar", "foo", "baz", "bar"))
        assertEquals("""["foo","bar","baz"]""", json)
    }

    @Test
    fun encodeSetReturnsNullForEmptyInputs() {
        assertNull(Naksha.encodeSet(null))
        assertNull(Naksha.encodeSet(TagList()))
    }

    @Test
    fun decodeSetRoundTrip() {
        val original = TagList("foo", "bar=baz", "name:=42")
        val json = Naksha.encodeSet(original)
        val decoded = Naksha.decodeSet(json)
        val asStrings: List<String?> = decoded?.toList() ?: emptyList()
        assertEquals(listOf("foo", "bar=baz", "name:=42"), asStrings)
    }

    @Test
    fun decodeSetReturnsNullForBlankInputs() {
        assertNull(Naksha.decodeSet(null))
        assertNull(Naksha.decodeSet(""))
        assertNull(Naksha.decodeSet("   "))
    }
}
