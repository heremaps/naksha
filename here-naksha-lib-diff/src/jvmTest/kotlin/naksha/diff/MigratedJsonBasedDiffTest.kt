package naksha.diff

import naksha.base.Platform
import naksha.base.PlatformUtil
import naksha.base.PlatformUtil.PlatformUtilCompanion.deepEquals
import naksha.diff.*
import org.json.JSONException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * All of these tests were copied and ported from v2 version of diff-utils tests.
 *
 * The original test was placed under 'here-naksha-lib-core':
 * com.here.naksha.lib.core.util.diff.PatcherTest
 *
 * The only adjustments that were performed due to:
 * - Java->Kotlin migration
 * - renamed & moved functions of tested code and other notable things:
 *  - `Patcher.getDifference` is [DifferenceCalculator.calculateDifference]
 *  - `PatcherUtils.removeAllRemoveOp` is [DifferenceFilter.removeAllRemoveOp]
 *  - `Patcher.patch` remained as [Patcher.patch]
 *  - JSON (de)serialization happens via [Platform.toJSON] and [Platform.fromJSON]
 */
class MigratedJsonBasedDiffTest {
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
        val mapDiff34: MapDiff = diff34
        // TODO if possible to serialize Difference, simply compare the serialized Difference object with test file content
        assertIs<InsertDiff>(mapDiff34.differences["isAdded"])
        assertIs<UpdateDiff>(mapDiff34.differences["willBeUpdated"])
        assertIs<RemoveDiff>(mapDiff34.differences["firstToBeDeleted"])
        assertIs<MapDiff>(mapDiff34.differences["map"])
        assertIs<ListDiff>(mapDiff34.differences["array"])
        assertIs<RemoveDiff>(mapDiff34.differences["speedLimit"])

        // Assert nested layer
        val nestedMapDiff34 = mapDiff34.differences["map"] as MapDiff
        // "mapID" is retained, does not appear in nestedMapDiff34
        assertIs<InsertDiff>(nestedMapDiff34.differences["isAdded"])
        assertIs<UpdateDiff>(nestedMapDiff34.differences["willBeUpdated"])
        assertIs<RemoveDiff>(nestedMapDiff34.differences["willBeDeleted"])

        // Assert nested array
        val nestedArrayDiff34 = mapDiff34.differences["array"] as ListDiff
        assertIs<UpdateDiff>(nestedArrayDiff34.differences[1])
        assertIs<MapDiff>(nestedArrayDiff34.differences[2])
        // "retainedElement" is retained, does not appear in nestedMapDiff34
        // InsertOp case for array (ListDiff) is addressed in the test testCompareSameArrayDifferentOrder()
        assertIs<RemoveDiff>(nestedArrayDiff34.differences[3])

        // Some extra nested JSON object in array assertions
        assertIs<InsertDiff>((nestedArrayDiff34.differences[2] as MapDiff).differences["isAddedProperty"])
        assertIs<UpdateDiff>((nestedArrayDiff34.differences[2] as MapDiff).differences["nestedShouldBeUpdated"])
        assertIs<RemoveDiff>((nestedArrayDiff34.differences[2] as MapDiff).differences["willBeDeletedProperty"])

        // Modify the whole difference to get rid of all RemoveOp
        DifferenceFilter.removeAllRemoveOp(mapDiff34)
        val patchedf3 = Patcher.patch(f3, mapDiff34)
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

    @Test
    @Throws(JSONException::class)
    fun testCompareSameArrayDifferentOrder() {
        val f3 = loadFeature("feature_3.json")
        assertNotNull(f3)
        val f5 = loadFeature("feature_5.json")
        assertNotNull(f5)

        val diff35 = DifferenceCalculator.calculateDifference(f3, f5)
        assertIs<MapDiff>(diff35)

        assertEquals(2, diff35.differences.size)
        assertTrue(diff35.differences["array"] is ListDiff)
        assertTrue(diff35.differences["speedLimit"] is RemoveDiff)
        val nestedArrayDiff35 = assertIs<ListDiff>(diff35.differences["array"])
        // The patcher compares array element by element in order,
        // so the nested JSON in feature 3 is compared against the string in feature 5
        // and the string in feature 3 is against the nested JSON in feature 5
        assertTrue(nestedArrayDiff35.differences[2] is UpdateDiff)
        assertTrue(nestedArrayDiff35.differences[3] is UpdateDiff)
        assertTrue(nestedArrayDiff35.differences[4] is InsertDiff)

        // Check that the patched feature 3 has the same content as 5
        val patchedf3Tof5 = Patcher.patch(f3, diff35)
        JSONAssert.assertEquals(
            Platform.toJSON(patchedf3Tof5),
            Platform.toJSON(f3),
            JSONCompareMode.STRICT
        )
        val newDiff = DifferenceCalculator.calculateDifference(patchedf3Tof5, f5)
        Assertions.assertNull(newDiff)
    }


    @Test
    fun testPatchingOnlyShuffledArrayProvided() {
        val f3 = loadFeature("feature_3.json")
        assertNotNull(f3)

        // feature 6 only contains the same array in feature 3, but with the order of the elements changed
        val f6 = loadFeature("feature_6.json")
        assertNotNull(f6)
        val diff36 = DifferenceCalculator.calculateDifference(f3, f6)
        assertNotNull(diff36)

        // Simulate REST API behaviour, ignore all RemoveOp type of Difference
        DifferenceFilter.removeAllRemoveOp(diff36)
        val patchedf3Tof6 = Patcher.patch(f3, diff36)
        val expectedPatchedf3 = loadFeature("feature_3_patched_with_6_no_remove_op.json")
        assertNotNull(expectedPatchedf3)

        JSONAssert.assertEquals(
            Platform.toJSON(expectedPatchedf3),
            Platform.toJSON(patchedf3Tof6),
            JSONCompareMode.STRICT
        )
        val newDiff36 = DifferenceCalculator.calculateDifference(patchedf3Tof6, expectedPatchedf3)
        assertNull(newDiff36)
    }

    @Test
    fun testIgnoreAll() {
        val ignoreAll = object : DefaultDiffContext() {
            override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean = true
        }

        val f1 = loadFeature("feature_1.json")
        assertNotNull(f1)

        val f2 = loadFeature("feature_2.json")
        assertNotNull(f2)

        val diff = DifferenceCalculator.calculateDifference(f1, f2, ignoreAll)
        Assertions.assertNull(diff)
    }

    @Test
    fun testXyzNamespace() {
        val ignoreSomeXyzKeys = object : DefaultDiffContext() {
            override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean =
                key in setOf(
                    "txn",
                    "txn_next",
                    "txn_uuid",
                    "uuid",
                    "puuid",
                    "version",
                    "rt_ts",
                    "createdAt",
                    "updatedAt",
                )
        }

        val f1 = loadFeature("feature_1.json")
        assertNotNull(f1)

        val f2 = loadFeature("feature_2.json")
        assertNotNull(f2)

        val diff = DifferenceCalculator.calculateDifference(f1, f2, ignoreSomeXyzKeys)

        assertIs<MapDiff>(diff)
        assertEquals(1, diff.differences.size)

        val propertiesDiff = diff.differences["properties"] as MapDiff
        assertEquals(1, propertiesDiff.differences.size)

        val xyzNsDiff = propertiesDiff.differences["@ns:com:here:xyz"] as MapDiff
        assertEquals(2, xyzNsDiff.differences.size)

        val actionDiff = xyzNsDiff.differences["action"]
        assertIs<UpdateDiff>(actionDiff)
        assertEquals("CREATE", actionDiff.oldValue)
        assertEquals("UPDATE", actionDiff.newValue)

        val tagsDiff = xyzNsDiff.differences["tags"]
        assertIs<ListDiff>(tagsDiff)
        assertEquals(23, tagsDiff.differences.size)
        for (i in 0 .. 21)  {
            assertNull(tagsDiff.differences[i])
        }
        val inserted = tagsDiff.differences[22]
        assertIs<InsertDiff>(inserted)
        assertEquals("utm_dummy_update", inserted.newValue)
    }

    @ParameterizedTest
    @MethodSource("listDiffSamples")
    fun testListDiff(before: List<*>?, after: List<*>?, expectedResult: ListDiff?) {
        // When:
        val difference = DifferenceCalculator.calculateDifference(before, after)

        // Then:
        assertTrue(deepEquals(expectedResult, difference))
    }

    private fun loadFeature(fileName: String): Any =
        Platform.fromJSON(getResourceAsText(fileName))
            ?: "Could not load/convert feature for filename: $fileName"

    private fun getResourceAsText(fileName: String): String =
        javaClass.getResource(JSON_DIFF_RESOURCES + fileName)?.readText()
            ?: throw IllegalArgumentException("Could not find/read text file: $JSON_DIFF_RESOURCES$fileName")

    companion object {
        private const val JSON_DIFF_RESOURCES = "/json_diff/"

        @JvmStatic
        private fun listDiffSamples(): Stream<Arguments> {
            return Stream.of(
                Arguments.arguments(
                    listOf("one", "two"),
                    listOf("one", "three"),
                    listDiff(2, 2,
                        null,
                        UpdateDiff("two", "three"))
                ),
                Arguments.arguments(
                    listOf("one", "two", "three"),
                    listOf("three", "four"),
                    listDiff(3, 2,
                        UpdateDiff("one", "three"),
                        UpdateDiff("two", "four"),
                        RemoveDiff("three")
                    )
                ),
                Arguments.arguments(
                    listOf("one", "two"),
                    listOf("three", "four", "five"),
                    listDiff( 2, 3,
                        UpdateDiff("one", "three"),
                        UpdateDiff("two", "four"),
                        InsertDiff("five")
                    )
                ),
                Arguments.arguments(
                    listOf<Any>(),
                    listOf("one", "two", "three"),
                    listDiff( 0, 3,
                        InsertDiff("one"),
                        InsertDiff("two"),
                        InsertDiff("three")
                    )
                ),
                Arguments.arguments(
                    listOf("one", "two", "three"),
                    listOf<Any>(),
                    listDiff(3, 0,
                        RemoveDiff("one"),
                        RemoveDiff("two"),
                        RemoveDiff("three")
                    )
                )
            )
        }

        private fun listDiff(originalLength:Int, newLength:Int, vararg diffs: Difference?): ListDiff {
            val listDiff = ListDiff()
            listDiff.originalLength = originalLength
            listDiff.newLength = newLength
            listDiff.differences.addAll(listOf(*diffs))
            return listDiff
        }
    }
}
