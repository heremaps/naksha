package naksha.base

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PTypedArrayRemovalTest {


    @Test
    fun shouldRemoveInIterator(){
        // Given
        val pTypedArray = PTypedArray<String>( String::class)
        pTypedArray.addAll( listOf(
            "one",
            "two",
            "three"
        ))

        // When
        val iterator = pTypedArray.iterator()
        while(iterator.hasNext()){
            if(iterator.next() == "two"){
                iterator.remove()
            }
        }

        // Then:
        assertEquals(2, pTypedArray.size)
        assertTrue(pTypedArray.containsAll(listOf("one", "three")))
    }
}