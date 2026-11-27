package naksha.psql

import naksha.base.Id
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.Tuple
import naksha.model.objects.StandardMembers
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgRowsTest {
    @Test
    fun shouldStoreHeadAndFiniteNextVersionsWithoutNullTranslation() {
        val rows = PgRows()
            .addColumn(PgColumn.FnColumn)
            .addColumn(PgColumn.VersionColumn)
            .addColumn(PgColumn.NextVersionColumn)
        for (nextVersion in listOf(null, Version.HEAD.number, 100L)) {
            val members = HeapBook(BookType.MEMBER_BOOK)
            members.put(StandardMembers.TN, TupleNumber(1L, 1, 1, 42L, 4L))
            members.put(StandardMembers.NEXT_VERSION, nextVersion)
            val tuple = Tuple(featureBytes = ByteArray(16), membersBook = members)
            rows.setRow(0, tuple)
            val expected = nextVersion ?: Version.HEAD.number
            assertEquals(expected, rows.getLong(0, PgColumn.NextVersionColumn))
            assertEquals(expected, rows.getColumn(PgColumn.NextVersionColumn.name)!!.toArray()[0])
        }
        rows.getColumn(PgColumn.NextVersionColumn.name)!!.values[0] = null
        assertNull(rows.getLong(0, PgColumn.NextVersionColumn))
        assertTrue(PgColumn.NextVersionColumn.extra!!.contains("NOT NULL"))
    }

    @Test
    fun shouldAlwaysConvertSqlFeatureNumbersToOrdinal() {
        val rows = PgRows()
            .addColumn(PgColumn.FnColumn)
            .addColumn(PgColumn.FnColumn, "existing_fn")
            .addColumn(PgColumn.VersionColumn)
            .addColumn("other_int64", MemberType.INT64)
        val features = listOf(0L, 1L, 128L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)
        for (i in features.indices) {
            val fn = features[i]
            rows.setColumn(PgColumn.FnColumn.name, i, fn)
            rows.setColumn("existing_fn", i, fn)
            rows.setColumn(PgColumn.VersionColumn.name, i, fn)
            rows.setColumn("other_int64", i, fn)
        }
        rows.size = features.size + 1
        repeat(2) {
            val bindings = rows.values()
            for (i in features.indices) {
                val logical = features[i]
                val ordinal = Id.featureNumberToOrdinal(logical)
                for (column in 0..3) {
                    assertEquals(if (column < 2) ordinal else logical, (bindings[column] as Array<*>)[i])
                }
                assertEquals(logical, rows.getLong(i, PgColumn.FnColumn))
                assertEquals(logical, rows.getLong(i, "existing_fn"))
                assertEquals(ordinal, rows.columns[0].values[i])
                assertEquals(ordinal, rows.columns[1].values[i])
            }
            for (column in bindings) assertNull((column as Array<*>)[features.size])
        }
    }

    @Test
    fun shouldDecodeOrdinalFeatureAliasesBeforeExposingIdentity() {
        val rows = PgRows()
            .addColumn(PgColumn.FnColumn)
            .addColumn(PgColumn.FnColumn, "existing_fn")
            .addColumn("other_int64", MemberType.INT64)
        rows.size = 1
        for (logical in listOf(0L, 1L, 128L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val ordinal = Id.featureNumberToOrdinal(logical)
            for (column in rows.columns) column.values[0] = ordinal
            assertEquals(logical, rows.getLong(0, PgColumn.FnColumn))
            assertEquals(logical, rows.getLong(0, "existing_fn"))
            assertEquals(ordinal, rows.getLong(0, "other_int64"))
            for (binding in rows.values()) assertEquals(ordinal, (binding as Array<*>)[0])
            for (column in rows.columns) column[0] = null
            for (column in rows.columns) assertNull(column[0])
        }
    }

    @Test
    fun shouldGrowRowsAndRetainValues() {
        val rows = PgRows()
            .addColumn("name", MemberType.STRING)
            .addColumn("count", MemberType.INT32)

        assertEquals(0, rows.size)
        assertEquals(0, rows.capacity)

        assertTrue(rows.setColumn("name", 0, "first"))
        assertTrue(rows.setColumn("count", 0, 1))
        assertEquals(1, rows.size)
        assertTrue(rows.capacity >= rows.size)

        assertTrue(rows.setColumn("name", 5, "sixth"))
        assertTrue(rows.setColumn("count", 5, 6))
        assertEquals(6, rows.size)
        assertTrue(rows.capacity >= rows.size)
        assertEquals("first", rows.getString(0, "name"))
        assertEquals(1, rows.getInt(0, "count"))
        assertNull(rows.getAny(1, "name"))
        assertEquals("sixth", rows.getString(5, "name"))
        assertEquals(6, rows.getInt(5, "count"))

        val capacity = rows.capacity
        rows.size = 2
        assertEquals(2, rows.size)
        assertEquals(capacity, rows.capacity)
        assertNull(rows.getAny(5, "name"))
        assertEquals("first", rows.getString(0, "name"))
    }
}
