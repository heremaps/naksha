package naksha.base

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MapProxyTest {

    @Test
    fun shouldRemoveInIterator() {
        // Given
        val mapProxy = MapProxy(Int::class, String::class)
        mapProxy.putAll(
            mapOf(
                1 to "one",
                2 to "two",
                3 to "three"
            )
        )

        // When
        val iterator = mapProxy.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key == 2) {
                iterator.remove()
            }
        }

        // Then:
        assertEquals(2, mapProxy.size)
        assertEquals("one", mapProxy[1])
        assertEquals("three", mapProxy[3])
    }

    @Test
    fun shouldUpdateContentViaEntries() {
        // Given
        val mapProxy = MapProxy(Int::class, String::class)
        mapProxy.putAll(
            mapOf(
                1 to "one",
                2 to "two",
                3 to "three"
            )
        )

        // When
        mapProxy.entries.forEach { entry ->
            when (entry.key) {
                1 -> entry.setValue("new_one")
                2 -> entry.setValue(null)
            }
        }

        // Then:
        assertEquals(3, mapProxy.size)
        assertEquals("new_one", mapProxy[1])
        assertEquals(null, mapProxy[2])
        assertEquals("three", mapProxy[3])
    }
}