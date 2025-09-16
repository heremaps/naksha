package naksha.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        DifferenceFilter.removeAllRemoveOpFromMaps(mapDiff)

        // Then
        assertEquals(3, mapDiff.size)
        val filteredKeys = setOf("simpleUpdate", "simpleInsert", "nestedDiff")
        assertEquals(filteredKeys, mapDiff.keys.toSet())
        val filteredNestedList = (mapDiff["nestedDiff"] as MapDiff)["nestedList"] as ListDiff
        assertEquals(2, filteredNestedList.size)
        assertIs<UpdateOp>(filteredNestedList[0])
    }

    @Test
    fun shouldNotRemoveOpsFromList() {
        // Given
        val listDiff = ListDiff()
        listDiff.add(UpdateOp("old_1", "new_1"))
        listDiff.add(RemoveOp("del_1"))
        listDiff.add(InsertOp("in_1"))

        // When
        DifferenceFilter.removeAllRemoveOpFromMaps(listDiff)

        // Then
        assertEquals(3, listDiff.size)
        assertTrue(listDiff.any { it is UpdateOp })
        assertTrue(listDiff.any { it is RemoveOp })
        assertTrue(listDiff.any { it is InsertOp })
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