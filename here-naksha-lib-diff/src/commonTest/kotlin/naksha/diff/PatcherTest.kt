package naksha.diff

import naksha.base.Base
import kotlin.test.*

class PatcherTest {

    @Test
    fun shouldReturnOriginalOnMissingDiff(){
        // Given
        val toBePatched = mapOf("foo" to "bar")

        // When
        val patched = Patcher.patch(toBePatched = toBePatched, diff = null)

        // Then
        assertEquals(toBePatched, patched)
    }

    @Test
    fun shouldFailOnNotMatchingDiff(){
        assertFailsWith<IllegalArgumentException> {
            Patcher.patch(toBePatched = mapOf("foo" to "bar"), diff = ListDiff())
        }
        assertFailsWith<IllegalArgumentException> {
            Patcher.patch(toBePatched = listOf(1, 2, 3), diff = MapDiff())
        }
    }

    @Test
    fun shouldFailOnUnsupportedObject(){
        assertFailsWith<IllegalArgumentException> {
            Patcher.patch("Not map or list", MapDiff())
        }
    }

    @Test
    fun shouldPatchMap(){
        // Given:
        val patchedObject = Base.fromJSON(
            """
            {
                "name": "John",
                "age": 37,
                "address": {
                    "city": "London",
                    "street": "Abbey Road",
                    "houseNo": 12
                }
            }
        """.trimIndent())!!

        // And:
        val patch = MapDiff()
        patch["age"] = UpdateOp(oldValue = 37, newValue = 41)
        val addressPatch = MapDiff()
        addressPatch["city"] = UpdateOp(oldValue = "London", newValue = "Fordwich")
        addressPatch["street"] = RemoveOp(oldValue = "Abbey Road")
        addressPatch["houseNo"] = UpdateOp(oldValue = 12, newValue = 71)
        patch["address"] = addressPatch


        // When
        Patcher.patch(patchedObject, patch)

        // Then
        val patchedObjectAsMap = patchedObject as Map<Any, Any>
        assertEquals(41, patchedObjectAsMap["age"])
        val patchedAddress = patchedObjectAsMap["address"] as Map<Any, Any>
        assertEquals("Fordwich", patchedAddress["city"])
        assertTrue("street" !in patchedAddress)
        assertEquals(71, patchedAddress["houseNo"])
    }

    @Test
    fun shouldPatchList(){
        // Given
        val patchedList = mutableListOf(0, "one", 2, "three")

        // And
        val listDiff = ListDiff()
        listDiff.add(null) // leaving 0 as is
        listDiff.add(UpdateOp(oldValue = "one", newValue = 1)) // "one" -> 1
        listDiff.add(RemoveOp(oldValue = 2)) // remove 2
        listDiff.add(InsertOp(newValue = 4)) // insert 4

        // When
        Patcher.patch(patchedList, listDiff)

        // Then
        assertEquals(4, patchedList.size)
        assertEquals(0, patchedList[0])
        assertEquals(1, patchedList[1])
        assertEquals("three", patchedList[2])
        assertEquals(4, patchedList[3])
    }
}