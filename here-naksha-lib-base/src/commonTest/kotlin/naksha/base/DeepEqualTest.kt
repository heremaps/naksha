package naksha.base

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Dedicated to test PlatformUtil.deepEquals(). This is needed because as of Oct 2024, Kotlin
 * has not supported comparing array contents using == yet.
 */
class DeepEqualTest {
    @Test
    fun nestedArrayInMap() {
        val obj1 = AnyObject()
        obj1["foo"] = "bar"
        val obj2 = AnyObject()
        obj2["foo"] = "bar"
        assertNotSame(obj1, obj2, "Two objects initiated by constructor are somehow the same!")
        obj1["array"] = arrayOf("a", "b", "c")
        obj2["array"] = arrayOf("a", "b", "c")
        assertNotEquals(obj1, obj2, "Check if Kotlin now perform deep equal comparison on array contents!")
        assertTrue(PlatformUtil.deepEquals(obj1,obj2), "PlatformUtil.deepEqual() not working!")
    }

    @Test
    fun nestedListInArrayInMap() {
        val obj1 = AnyObject()
        val obj2 = AnyObject()
        obj1["array"] = arrayOf("a", arrayOf("x", AnyObject().addAll("foo","bar")), listOf(1,2))
        obj2["array"] = arrayOf("a", arrayOf("x", AnyObject().addAll("foo","bar")), listOf(1,2))
        assertNotEquals(obj1, obj2, "Check if Kotlin now perform deep equal comparison on array contents!")
        assertTrue(PlatformUtil.deepEquals(obj1,obj2), "PlatformUtil.deepEqual() not working!")
    }
}