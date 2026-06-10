package naksha.psql

import naksha.base.Int64
import naksha.model.TagList
import naksha.model.TupleNumber
import naksha.model.Version
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PgSetCoerceTest {

    @Test
    fun coerceSetEmitsJsonArrayPreservingInsertionOrder() {
        val out = PgCustomMemberValues.coerce(
            TagList("zebra", "apple", "mango"), MemberType.SET, "fid", "tags"
        ) as String
        assertEquals("""["zebra","apple","mango"]""", out)
    }

    @Test
    fun coerceSetDropsDuplicates() {
        val out = PgCustomMemberValues.coerce(
            TagList("foo", "bar", "foo", "baz", "bar"), MemberType.SET, "fid", "tags"
        ) as String
        assertEquals("""["foo","bar","baz"]""", out)
    }

    @Test
    fun coerceSetAcceptsMixedPrimitives() {
        val out = PgCustomMemberValues.coerce(
            listOf<Any?>(true, Int64(42), 3.14, "foo"),
            MemberType.SET, "fid", "mixed"
        ) as String
        assertEquals("""[true,42,3.14,"foo"]""", out)
    }

    @Test
    fun coerceSetStringifiesTupleNumber() {
        val tn = TupleNumber(
            storageNumber = Int64(1L),
            mapNumber = 2,
            collectionNumber = 3,
            featureNumber = Int64(42L),
            version = Version(Int64(100L))
        )
        val expected = """["${tn}"]"""
        val out = PgCustomMemberValues.coerce(
            listOf<Any?>(tn), MemberType.SET, "fid", "tn_set"
        ) as String
        assertEquals(expected, out)
    }

    @Test
    fun coerceSetReturnsNullForEmptyOrNullInputs() {
        assertNull(PgCustomMemberValues.coerce(null, MemberType.SET, "fid", "tags"))
        assertNull(PgCustomMemberValues.coerce(TagList(), MemberType.SET, "fid", "tags"))
        assertNull(PgCustomMemberValues.coerce(emptyList<Any?>(), MemberType.SET, "fid", "tags"))
    }

    @Test
    fun coerceSetRejectsNonPrimitiveEntry() {
        // A map value is not a primitive — coerceSet returns null and logs a warning.
        val out = PgCustomMemberValues.coerce(
            listOf<Any?>("foo", mapOf("nested" to 1)),
            MemberType.SET, "fid", "tags"
        )
        assertNull(out)
    }
}
