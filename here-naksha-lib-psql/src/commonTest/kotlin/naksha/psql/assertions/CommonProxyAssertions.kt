package naksha.psql.assertions

import naksha.base.PAnyArray
import naksha.base.PAnyMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Custom assertions to be performed on [PAnyMap]
 */
object CommonProxyAssertions {

    fun assertAnyObjectsEqual(left: PAnyMap?, right: PAnyMap?) {
        checkNullsAndDelegate(left, right, this::assertNonNullObjectsEqual)
    }

    fun assertAnyListsEqual(left: PAnyArray?, right: PAnyArray?, bearerName: String? = null) {
        checkNullsAndDelegate(left, right) { l, r -> assertNonNullListsEqual(l, r, bearerName) }
    }

    private fun assertNonNullObjectsEqual(left: PAnyMap, right: PAnyMap) {
        left.keys.intersect(right.keys).forEach { commonKey ->
            when (val leftVal = left[commonKey]) {
                null -> assertNull(
                    right[commonKey],
                    "Left value for $commonKey is null, right is not"
                )

                is PAnyMap -> assertAnyObjectsEqual(leftVal, right[commonKey] as PAnyMap)
                is PAnyArray -> assertAnyListsEqual(leftVal, right[commonKey] as PAnyArray, commonKey)
                else -> assertEquals(
                    leftVal,
                    right[commonKey],
                    "Raw comparison failed for: $commonKey"
                )
            }
        }
        // one of our objects might not have all props that right one has
        // it's ok if the props on the right are logically empty
        val keysMissingOnTheRight = left.keys - right.keys
        keysMissingOnTheRight.forEach { key ->
            assertTrue("Right object is missing nonempty property: $key") {
                isLogicallyEmpty(left[key])
            }
        }
        val keysMissingOnTheLeft = right.keys - left.keys
        keysMissingOnTheLeft.forEach { key ->
            assertTrue("Left object is missing nonempty property: $key") {
                isLogicallyEmpty(right[key])
            }
        }
    }

    private fun assertNonNullListsEqual(left: PAnyArray, right: PAnyArray, bearerName: String? = null) {
        left.forEachIndexed { index, value ->
            if (value is PAnyMap) {
                assertAnyObjectsEqual(value, right[index] as PAnyMap)
            } else {
                assertEquals(
                    value,
                    right[index],
                    "Raw comparison failed for ${bearerName?.let { "$it, " }}index: $index"
                )
            }
        }
    }

    private fun isLogicallyEmpty(value: Any?): Boolean {
        return when (value) {
            null -> true
            is PAnyArray -> value.isEmpty()
            is PAnyMap -> value.isEmpty() || value.all { (_, child) -> isLogicallyEmpty(child) }
            else -> false
        }
    }

    private fun <T> checkNullsAndDelegate(left: T?, right: T?, onNotNulls: (T, T) -> Unit) {
        if (left == null) {
            assertEquals(left, right)
        } else {
            assertNotNull(right, "right is null, but left is not")
            onNotNulls(left, right)
        }
    }
}