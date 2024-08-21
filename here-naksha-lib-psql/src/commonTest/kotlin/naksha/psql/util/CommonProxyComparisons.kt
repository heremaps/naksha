package naksha.psql.util

import naksha.base.AnyList
import naksha.base.AnyObject
import kotlin.contracts.contract
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Custom assertions to be performed on [AnyObject]
 */
object CommonProxyComparisons {

    fun assertAnyObjectsEqual(left: AnyObject, right: AnyObject, ignorePaths: Set<String> = emptySet()) {
        left.keys.intersect(right.keys).forEach { commonKey ->
            if(commonKey !in ignorePaths){
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
        }
        // one of our objects might not have all props that right one has
        // it's ok if the props on the right are logically empty
        val keysMissingOnTheRight = left.keys - right.keys
        keysMissingOnTheRight.forEach { key ->
            if(key !in ignorePaths){
                assertTrue("Right object is missing nonempty property: $key") {
                    isLogicallyEmpty(left[key])
                }
            }
        }
        val keysMissingOnTheLeft = right.keys - left.keys
        keysMissingOnTheLeft.forEach { key ->
            if(key !in ignorePaths){
                assertTrue("Left object is missing nonempty property: $key") {
                    isLogicallyEmpty(right[key])
                }
            }
        }
    }

    fun assertAnyListsEqual(left: AnyList, right: AnyList, bearerName: String) {
        left.forEachIndexed { index, value ->
            if (value is AnyObject) {
                assertAnyObjectsEqual(value, right[index] as AnyObject)
            } else {
                assertEquals(
                    value,
                    right[index],
                    "Raw comparison failed for $bearerName, index: $index"
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

    // TODO (Jakub): incorporate in comaprison instead of xyzNamespace
    sealed class PropertyPath(open val currentNode: String) {
        abstract fun next(): PropertyPath?

        companion object {
            fun of(vararg properties: String): PropertyPath {
                return when(properties.size){
                    0 -> throw IllegalStateException("Required at least single property")
                    1 -> FinalProperty(properties[0])
                    else -> NestedProperty(*properties)
                }
            }
        }

        data class FinalProperty(override val currentNode: String) : PropertyPath(currentNode){
            override fun next(): PropertyPath? = null

        }

        class NestedProperty(val topToBottomProperties: List<String>): PropertyPath(topToBottomProperties.first()){
            constructor(vararg topToBottomProperties: String): this(topToBottomProperties.toList())

            override fun next(): PropertyPath? {
                val subProperties = topToBottomProperties.drop(1)
                return if(subProperties.size == 1){
                    FinalProperty(subProperties.first())
                } else {
                    NestedProperty(subProperties)
                }
            }
        }
    }

}