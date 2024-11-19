package naksha.base

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MapProxyRemovalTest {

    @Test
    fun shouldRemoveInIterator(){
        // Given
        val mapProxy = MapProxy<Int, String>(Int::class, String::class)
        mapProxy.putAll(mapOf(
            1 to "one",
            2 to "two",
            3 to "three"
        ))

        // When
        val iterator = mapProxy.iterator()
        while(iterator.hasNext()){
            if(iterator.next().key == 2){
                iterator.remove()
            }
        }

        // Then:
        assertEquals(2, mapProxy.size)
        assertEquals("one", mapProxy[1])
        assertEquals("three", mapProxy[3])
    }
}