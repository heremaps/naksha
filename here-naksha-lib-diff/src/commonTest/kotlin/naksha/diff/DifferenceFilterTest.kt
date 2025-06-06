package naksha.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DifferenceFilterTest {

    @Test
    fun shouldFilterRemoveOpsFromMap() {
        // Given
        val mapDiff = MapDiff().apply {
            differences["simpleUpdate"] = UpdateDiff("old_1", "new_1")
            differences["simpleRemoval"] = RemoveDiff("del_1")
            differences["simpleInsert"] = InsertDiff("in_1")
            differences["nestedDiff"] = MapDiff().apply {
                differences["nestedList"] = ListDiff().apply {
                    differences.add(UpdateDiff("old_2", "new_2"))
                    differences.add(RemoveDiff("del_2"))
                }
            }
        }

        // When
        DifferenceFilter.removeAllRemoveOp(mapDiff)

        // Then
        assertEquals(3, mapDiff.differences.size)
        val filteredKeys = setOf("simpleUpdate", "simpleInsert", "nestedDiff")
        assertEquals(filteredKeys, mapDiff.differences.keys.toSet())
        val filteredNestedList = (mapDiff.differences["nestedDiff"] as MapDiff).differences["nestedList"] as ListDiff
        assertEquals(1, filteredNestedList.differences.size)
        assertIs<UpdateDiff>(filteredNestedList.differences[0])
    }

    @Test
    fun x(){
        val map = mutableMapOf(
            1 to "one",
            2 to "two",
            3 to "three"
        )

        val iterator = map.iterator()
        while(iterator.hasNext()){
            val entry = iterator.next()
            if(entry.key == 2){
                iterator.remove()
            }
        }

        assertEquals(2, map.size)
    }
}