import com.here.naksha.lib.base.P_List
import com.here.naksha.lib.base.P_Map
import com.here.naksha.lib.base.PlatformMapApi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
            assertEquals("foo", next.value!!.proxy(TestStringList::class)[0])
            assertEquals("world", next.value!!.proxy(TestStringList::class)[1])

            next = entryIt.next()
            assertNotNull(next)
            assertFalse(next.done)
            assertEquals("bar", next.value!!.proxy(TestStringList::class)[0])
            assertEquals("hello", next.value!!.proxy(TestStringList::class)[1])
        }
    }
}