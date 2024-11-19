package naksha.diff

import naksha.base.Platform
import naksha.diff.*
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// TODO: finish - this should be copy of old `naksha.diff.PatcherTest`
internal class JsonBasedDiffTest {
    @Test
    fun basic() {
        // Given:
        val feature1 = loadFeature("feature_1.json")
        val feature2 = loadFeature("feature_2.json")

        // When
        val diffBeforePatching = DifferenceCalculator.calculateDifference(feature1, feature2)

        // And
        Patcher.patch(feature1, diffBeforePatching)

        // And
        val diffAfterPatching = DifferenceCalculator.calculateDifference(feature1, feature2)

        // Then:
        assertNotNull(diffBeforePatching)
        assertNull(diffAfterPatching)
    }

    @Test
    fun testCompareBasicNestedJson() {
        val f3 = loadFeature("feature_3.json")
        val f4 = loadFeature("feature_4.json")

        val diff34 = DifferenceCalculator.calculateDifference(f3, f4)
        assertIs<MapDiff>(diff34)

        // Assert outermost layer
        val mapDiff34: MapDiff = diff34 as MapDiff
        // TODO if possible to serialize Difference, simply compare the serialized Difference object with test file content
        assertIs<InsertOp>(mapDiff34["isAdded"])
        assertIs<UpdateOp>(mapDiff34["willBeUpdated"])
        assertIs<RemoveOp>(mapDiff34["firstToBeDeleted"])
        assertIs<MapDiff>(mapDiff34["map"])
        assertIs<ListDiff>(mapDiff34["array"])
        assertIs<RemoveOp>(mapDiff34["speedLimit"])

        // Assert nested layer
        val nestedMapDiff34 = mapDiff34["map"] as MapDiff
        // "mapID" is retained, does not appear in nestedMapDiff34
        assertIs<InsertOp>(nestedMapDiff34["isAdded"])
        assertIs<UpdateOp>(nestedMapDiff34["willBeUpdated"])
        assertIs<RemoveOp>(nestedMapDiff34["willBeDeleted"])

        // Assert nested array
        val nestedArrayDiff34= mapDiff34["array"] as ListDiff
        assertIs<UpdateOp>(nestedArrayDiff34[1])
        assertIs<MapDiff>(nestedArrayDiff34[2])
        // "retainedElement" is retained, does not appear in nestedMapDiff34
        // InsertOp case for array (ListDiff) is addressed in the test testCompareSameArrayDifferentOrder()
        assertIs<RemoveOp>(nestedArrayDiff34[3])

        // Some extra nested JSON object in array assertions
        assertIs<InsertOp>((nestedArrayDiff34[2] as MapDiff)["isAddedProperty"])
        assertIs<UpdateOp>((nestedArrayDiff34[2] as MapDiff)["nestedShouldBeUpdated"])
        assertIs<RemoveOp>((nestedArrayDiff34[2] as MapDiff)["willBeDeletedProperty"])

        // Modify the whole difference to get rid of all RemoveOp
        val newDiff34 = DifferenceFilter.removeAllRemoveOp(mapDiff34)
        val patchedf3= Patcher.patch(f3, newDiff34)
        assertNotNull(patchedf3)
        val expectedPatchedf3 = loadFeature("feature_3_patched_to_4_no_remove.json")
        assertNotNull(expectedPatchedf3)

        // Check that the patched feature 3 has the correct content as 4 but no JSON properties deleted
        JSONAssert.assertEquals(
            Platform.toJSON(expectedPatchedf3),
            Platform.toJSON(patchedf3),
            JSONCompareMode.STRICT
        )
        val newDiff = DifferenceCalculator.calculateDifference(patchedf3, expectedPatchedf3)
        assertNull(newDiff)
    }


    private fun loadFeature(fileName: String): Any =
        Platform.fromJSON(getResourceAsText(fileName))
            ?: "Could not load/convert feature for filename: $fileName"

    private fun getResourceAsText(fileName: String): String =
        javaClass.getResource(JSON_DIFF_RESOURCES + fileName)?.readText()
            ?: throw IllegalArgumentException("Could not find/read text file: $JSON_DIFF_RESOURCES$fileName")

    companion object {
        private const val JSON_DIFF_RESOURCES = "/json_diff/"
    }
}
