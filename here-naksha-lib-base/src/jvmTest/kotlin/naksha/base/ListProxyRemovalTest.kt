package naksha.base

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListProxyRemovalTest {


    @Test
    fun shouldRemoveInIterator(){
        // Given
        val listProxy = ListProxy<String>( String::class)
        listProxy.addAll( listOf(
            "one",
            "two",
            "three"
        ))

        // When
        val iterator = listProxy.iterator()
        while(iterator.hasNext()){
            if(iterator.next() == "two"){
                iterator.remove()
            }
        }

        // Then:
        assertEquals(2, listProxy.size)
        assertTrue(listProxy.containsAll(listOf("one", "three")))
    }
}