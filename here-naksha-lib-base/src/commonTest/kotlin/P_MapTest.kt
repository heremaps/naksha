import com.here.naksha.lib.base.P_List
import com.here.naksha.lib.base.P_Map
import com.here.naksha.lib.base.PlatformMapApi
import kotlin.test.*

internal class TestMapStringString : P_Map<String, String>(String::class, String::class)
internal class TestStringList : P_List<String>(String::class)

class P_MapTest {

    @Test
    fun createMapWithSomeKeysAndValues() {
        // given
        val map = TestMapStringString()

        map["foo"] = "world"
        assertEquals(1, map.size)
        assertEquals("world", map["foo"])

        map["bar"] = "hello"
        assertEquals(2, map.size)
        assertEquals("hello", map["bar"])

        // The order need to the insertion order!

        // Test keys.
        val keyIt = PlatformMapApi.map_key_iterator(map.data())
        assertNotNull(keyIt)
        apply {
            var next = keyIt.next()
            assertNotNull(next)
            assertFalse(next.done)
            assertEquals("foo", next.value)

            next = keyIt.next()
            assertNotNull(next)
            assertFalse(next.done)
            assertEquals("bar", next.value)
        }

        // Test values.
        val valueIt = PlatformMapApi.map_value_iterator(map.data())
        assertNotNull(keyIt)
        apply {
            var next = valueIt.next()
            assertNotNull(next)
            assertFalse(next.done)
            assertEquals("world", next.value)

            next = valueIt.next()
            assertNotNull(next)
            assertFalse(next.done)
            assertEquals("hello", next.value)
        }

        // Test entries.
        val entryIt = PlatformMapApi.map_iterator(map.data())
        assertNotNull(entryIt)
        apply {
            var next = entryIt.next()
            assertNotNull(next)
            assertFalse(next.done)
            var value = next.value
            check(value != null)
            var proxy = value.proxy(TestStringList::class)
            assertEquals("foo", proxy[0])
            assertEquals("world", proxy[1])

            next = entryIt.next()
            assertNotNull(next)
            value = next.value
            check(value != null)
            proxy = value.proxy(TestStringList::class)
            assertEquals("bar", proxy[0])
            assertEquals("hello", proxy[1])

            next = entryIt.next()
            assertTrue(next.done)
        }
    }

    @Test
    fun testEntries() {
        // Given: standard kotlin map
        val stdMap: Map<String, String?> = mapOf(
            "one" to "A",
            "two" to "B",
            "three" to "C"
        )

        // And: P_Map with items from stdMap
        val pMap = TestMapStringString().apply { putAll(stdMap) }

        // When: getting entry set
        val entries = pMap.entries

        // Then
        assertEquals(stdMap.entries.size, entries.size)
        entries.forEach { (key, value) ->
            assertEquals(stdMap[key], value)
        }
    }

    @Test
    fun testKeys() {
        // Given: a P_Map
        val pMap = pMap(
            "one" to "A",
            "two" to "B",
            "three" to "C"
        )

        // When: getting key set
        val keys = pMap.keys

        // Then
        assertEquals(3, keys.size)
        assertTrue {
            keys.containsAll(listOf("one", "two", "three"))
        }
    }

    @Test
    fun testValues() {
        // Given: a P_Map
        val pMap = pMap(
            "one" to "A",
            "two" to "B",
            "three" to "C"
        )

        // When: getting key set
        val values = pMap.values

        // Then
        assertEquals(3, values.size)
        assertTrue {
            values.containsAll(listOf("A", "B", "C"))
        }
    }

    @Test
    fun testPutAll() {
        // Given: a P_Map
        val firstMap = pMap(
            "one" to "A",
            "two" to "B",
            "three" to "C"
        )

        // And:
        val secondMap = pMap(
            "two" to "X",
            "four" to "Y"
        )

        // When
        firstMap.putAll(secondMap)

        // Then
        assertEquals(4, firstMap.size)
        listOf(
            "one" to "A",
            "two" to "X",
            "three" to "C",
            "four" to "Y"
        ).forEach { (key, value) ->
            assertEquals(firstMap[key], value)
        }
    }

    private fun pMap(vararg keyPairs: Pair<String, String>): P_Map<String, String> {
        val stdMap: Map<String, String?> = mapOf(*keyPairs)
        return TestMapStringString().apply { putAll(stdMap) }
    }
}