package naksha.base

import kotlin.test.*

class CMapTest {
    @Test
    fun basics() {
        val key = "Test"
        val value = 5
        val map = CMap<String, Any>()
        assertEquals(0, map.size)
        assertNull(map.putIfAbsent(key, value))
        assertEquals(1, map.size)
        assertSame(value, map.putIfAbsent(key, 10))
        assertSame(value, map.putIfAbsent(key, 11))
        map[key] = key
        assertSame(key, map.putIfAbsent(key, 11))
        assertTrue(map.remove(key, key))
        assertFalse(map.remove(key, key))
        assertEquals(0, map.size)
    }
}