package naksha.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DifferenceFilterTest {

    @Test
    fun shouldFilterRemoveOpsFromMap() {
        // Given
        val mapDiff = MapDiff()
        mapDiff["simpleUpdate"] = UpdateOp("old_1", "new_1")
        mapDiff["simpleRemoval"] = RemoveOp("del_1")
        mapDiff["simpleInsert"] = InsertOp("in_1")
        val nestedDiff = MapDiff()
        val nestedList = ListDiff()
        nestedList.add(UpdateOp("old_2", "new_2"))
        nestedList.add(RemoveOp("del_2"))
        nestedDiff["nestedList"] = nestedList
        mapDiff["nestedDiff"] = nestedDiff

        // When
        DifferenceFilter.removeAllRemoveOp(mapDiff)

        // Then
        assertEquals(3, mapDiff.size)
        val filteredKeys = setOf("simpleUpdate", "simpleInsert", "nestedDiff")
        assertEquals(filteredKeys, mapDiff.keys.toSet())
        val filteredNestedList = (mapDiff["nestedDiff"] as MapDiff)["nestedList"] as ListDiff
        assertEquals(1, filteredNestedList.size)
        assertIs<UpdateOp>(filteredNestedList[0])
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