package naksha.diff

import naksha.base.Platform
import org.json.JSONException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
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
 *  - `PatcherUtils.removeAllRemoveOp` is [DifferenceFilter.removeAllRemoveOpFromMaps]
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
        val nestedArrayDiff34 = mapDiff34["array"] as ListDiff
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
        DifferenceFilter.removeAllRemoveOpFromMaps(mapDiff34)
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

        assertEquals(2, diff35.size)
        assertTrue(diff35["array"] is ListDiff)
        assertTrue(diff35["speedLimit"] is RemoveOp)
        val nestedArrayDiff35 = diff35["array"] as ListDiff
        // The patcher compares array element by element in order,
        // so the nested JSON in feature 3 is compared against the string in feature 5
        // and the string in feature 3 is against the nested JSON in feature 5
        assertTrue(nestedArrayDiff35[2] is UpdateOp)
        assertTrue(nestedArrayDiff35[3] is UpdateOp)
        assertTrue(nestedArrayDiff35[4] is InsertOp)

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
        DifferenceFilter.removeAllRemoveOpFromMaps(diff36)
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
        val ignoreAll = object : DiffContext {
            override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean =
                true

            override fun areTwoNumbersEqual(first: Number, second: Number): Boolean =
                DiffContext.Default.areTwoNumbersEqual(first, second)
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
        val ignoreSomeXyzKeys = object : DiffContext {
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

            override fun areTwoNumbersEqual(first: Number, second: Number): Boolean =
                DiffContext.Default.areTwoNumbersEqual(first, second)
        }

        val f1 = loadFeature("feature_1.json")
        assertNotNull(f1)

        val f2 = loadFeature("feature_2.json")
        assertNotNull(f2)

        val diff = DifferenceCalculator.calculateDifference(f1, f2, ignoreSomeXyzKeys)

        assertIs<MapDiff>(diff)
        assertEquals(1, diff.size)

        val propertiesDiff = diff["properties"] as MapDiff
        assertEquals(1, propertiesDiff.size)

        val xyzNsDiff = propertiesDiff["@ns:com:here:xyz"] as MapDiff
        assertEquals(2, xyzNsDiff.size)

        val actionDiff = xyzNsDiff["action"]
        assertIs<UpdateOp>(actionDiff)
        assertEquals("CREATE", actionDiff.oldValue)
        assertEquals("UPDATE", actionDiff.newValue)

        val tagsDiff = xyzNsDiff["tags"]
        assertIs<ListDiff>(tagsDiff)
        assertEquals(23, tagsDiff.size)
        for (i in 0 .. 21)  {
            assertNull(tagsDiff[i])
        }
        val inserted = tagsDiff[22]
        assertIs<InsertOp>(inserted)
        assertEquals("utm_dummy_update", inserted.newValue)
    }

    @ParameterizedTest
    @MethodSource("listDiffSamples")
    fun testListDiff(before: List<*>?, after: List<*>?, expectedResult: ListDiff?) {
        // When:
        val difference = DifferenceCalculator.calculateDifference(before, after)

        // Then:
        assertEquals(expectedResult, difference)
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
                    listOf("one", "two"), listOf("one", "three"),
                    listDiff(
                        null,
                        UpdateOp("two", "three")
                    )
                ),
                Arguments.arguments(
                    listOf("one", "two", "three"), listOf("three", "four"),
                    listDiff(
                        UpdateOp("one", "three"),
                        UpdateOp("two", "four"),
                        RemoveOp("three")
                    )
                ),
                Arguments.arguments(
                    listOf("one", "two"), listOf("three", "four", "five"),
                    listDiff(
                        UpdateOp("one", "three"),
                        UpdateOp("two", "four"),
                        InsertOp("five")
                    )
                ),
                Arguments.arguments(
                    listOf<Any>(), listOf("one", "two", "three"),
                    listDiff(
                        InsertOp("one"),
                        InsertOp("two"),
                        InsertOp("three")
                    )
                ),
                Arguments.arguments(
                    listOf("one", "two", "three"), listOf<Any>(),
                    listDiff(
                        RemoveOp("one"),
                        RemoveOp("two"),
                        RemoveOp("three")
                    )
                )
            )
        }

        private fun listDiff(vararg diffs: Difference?): ListDiff {
            val listDiff = ListDiff()
            listDiff.addAll(listOf(*diffs))
            return listDiff
        }
    }
}
