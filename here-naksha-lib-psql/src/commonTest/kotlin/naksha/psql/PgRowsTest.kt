package naksha.psql

import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgRowsTest {
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
