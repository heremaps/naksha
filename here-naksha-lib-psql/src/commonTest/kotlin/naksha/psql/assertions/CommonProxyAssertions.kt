package naksha.psql.assertions

import naksha.base.AnyList
import naksha.base.AnyObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Custom assertions to be performed on [AnyObject]
 */
object CommonProxyAssertions {

    fun assertAnyObjectsEqual(left: AnyObject?, right: AnyObject?) {
        checkNullsAndDelegate(left, right, this::assertNonNullObjectsEqual)
    }

    fun assertAnyListsEqual(left: AnyList?, right: AnyList?, bearerName: String? = null) {
        checkNullsAndDelegate(left, right) { l, r -> assertNonNullListsEqual(l, r, bearerName) }
    }

    private fun assertNonNullObjectsEqual(left: AnyObject, right: AnyObject) {
        left.keys.intersect(right.keys).forEach { commonKey ->
            when (val leftVal = left[commonKey]) {
                null -> assertNull(
                    right[commonKey],
                    "Left value for $commonKey is null, right is not"
                )

                is AnyObject -> assertAnyObjectsEqual(leftVal, right[commonKey] as AnyObject)
                is AnyList -> assertAnyListsEqual(leftVal, right[commonKey] as AnyList, commonKey)
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

    private fun assertNonNullListsEqual(left: AnyList, right: AnyList, bearerName: String? = null) {
        left.forEachIndexed { index, value ->
            if (value is AnyObject) {
                assertAnyObjectsEqual(value, right[index] as AnyObject)
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
            is AnyList -> value.isEmpty()
            is AnyObject -> value.isEmpty() || value.all { (_, child) -> isLogicallyEmpty(child) }
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