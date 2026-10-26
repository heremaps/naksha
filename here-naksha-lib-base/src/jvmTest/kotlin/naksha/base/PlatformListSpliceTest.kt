package naksha.base

import naksha.base.PlatformListApi.PlatformListApiCompanion.array_splice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformListSpliceTest {
    private fun backed(json: Boolean, values: List<Any?> = emptyList()): JvmList =
        JvmList().also {
            it.list = if (json) JsonArray(values.toTypedArray()) else ArrayList(values)
        }

    private fun values(list: JvmList): List<Any?> = List(list.size) { list[it] }

    private fun removed(source: JvmList, start: Int, delete: Int, vararg adds: Any?): JvmList {
        val result = array_splice(source, start, delete, *adds)
        assertTrue(result is JvmList)
        assertNotSame(source, result)
        source.list?.let { assertNotSame(it, result.list) }
        return result
    }

    @Test
    fun emptyAndNoOpReturnFreshEmptyLists() {
        for (json in listOf(false, true)) {
            for (initial in listOf(emptyList(), listOf("a", "b"))) {
                val source = backed(json, initial)
                val first = removed(source, 0, 0)
                val second = removed(source, Int.MAX_VALUE, Int.MAX_VALUE)
                assertEquals(emptyList(), values(first))
                assertEquals(emptyList(), values(second))
                assertNotSame(first, second)
                assertEquals(initial, values(source))
                first.add("independent")
                assertEquals(emptyList(), values(second))
                assertEquals(initial, values(source))
            }
        }
        val unallocated = JvmList()
        assertEquals(emptyList(), values(removed(unallocated, Int.MIN_VALUE, Int.MAX_VALUE)))
        assertEquals(emptyList(), values(unallocated))
    }

    @Test
    fun extremeStartsAndDeletionCountsAreClamped() {
        for (json in listOf(false, true)) {
            val source = backed(json, listOf("a", "b", "c"))
            assertEquals(listOf("a"), values(removed(source, Int.MIN_VALUE, 1)))
            assertEquals(emptyList(), values(removed(source, Int.MAX_VALUE, Int.MAX_VALUE, "d")))
            assertEquals(listOf("b", "c", "d"), values(source))
            assertEquals(emptyList(), values(removed(source, -1, Int.MIN_VALUE, "x")))
            assertEquals(listOf("b", "c", "x", "d"), values(source))
            assertEquals(listOf("c", "x", "d"), values(removed(source, 1, Int.MAX_VALUE)))
            assertEquals(listOf("b"), values(source))
            assertEquals(listOf("b"), values(removed(source, Int.MIN_VALUE, Int.MAX_VALUE)))
            assertEquals(emptyList(), values(source))
        }
    }

    @Test
    fun replacesGrowsAndShrinksWithAnUnchangedTail() {
        for (json in listOf(false, true)) {
            val source = backed(json, listOf("a", "b", "c", "d", "e"))
            assertEquals(listOf("b", "c"), values(removed(source, 1, 2, "x", "y")))
            assertEquals(listOf("a", "x", "y", "d", "e"), values(source))
            assertEquals(listOf("x"), values(removed(source, 1, 1, 1, 2, 3)))
            assertEquals(listOf("a", 1, 2, 3, "y", "d", "e"), values(source))
            assertEquals(listOf(1, 2, 3, "y"), values(removed(source, 1, 4, "z")))
            assertEquals(listOf("a", "z", "d", "e"), values(source))
        }
    }

    @Test
    fun appendAndInsertIntoEmptyDoNotAddNullPadding() {
        for (json in listOf(false, true)) {
            val source = backed(json)
            assertEquals(emptyList(), values(removed(source, 0, 0, "a", "b")))
            assertEquals(listOf("a", "b"), values(source))
            assertEquals(emptyList(), values(removed(source, source.size, 0, "c")))
            assertEquals(listOf("a", "b", "c"), values(source))
            assertEquals(emptyList(), values(removed(source, Int.MAX_VALUE, 0, "d")))
            assertEquals(listOf("a", "b", "c", "d"), values(source))
            val unallocated = JvmList()
            removed(unallocated, Int.MAX_VALUE, 0, "only")
            assertEquals(listOf("only"), values(unallocated))
        }
    }

    @Test
    fun preservesRawValuesAndReferencesWithoutSharingStorage() {
        for (json in listOf(false, true)) {
            val rawArray = arrayOf<Any?>("nested")
            val rawList = JvmList("nested")
            val entries = listOf(null, Platform.UNDEFINED, Json.UNDEFINED, Any(), rawArray, rawList)
            val source = backed(json, listOf("head") + entries + "tail")
            val adds = entries.toTypedArray()
            val result = removed(source, 1, entries.size, *adds)
            assertEquals(entries.size, result.size)
            assertEquals(entries.size + 2, source.size)
            for (i in entries.indices) {
                assertSame(entries[i], result[i])
                assertSame(entries[i], source[i + 1])
            }
            adds[0] = "changed argument"
            assertSame(null, source[1])
            result[0] = "changed result"
            result.add("extra")
            assertSame(null, source[1])
            source[2] = "changed source"
            source.removeAt(source.size - 1)
            assertSame(Platform.UNDEFINED, result[1])
            assertEquals(entries.size + 1, result.size)
            assertEquals(entries.size + 1, source.size)
            assertEquals("head", source[0])
        }
    }

    @Test
    fun shrinkingClearsDiscardedJsonArrayCells() {
        val source = backed(true, List(8) { Any() })
        val backing = source.list as JsonArray
        val original = values(source)
        val result = removed(source, 1, 5, "replacement")
        assertEquals(original.subList(1, 6), values(result))
        assertEquals(listOf(original[0], "replacement", original[6], original[7]), values(source))
        for (i in source.size until original.size) {
            assertSame(Platform.UNDEFINED, backing.elements[i], "Discarded cell $i retains a reference")
        }
    }

    @Test
    fun bulkSpliceAvoidsElementByElementStructuralOperations() {
        for (json in listOf(false, true)) {
            val source = object : JvmList() {
                override fun removeAt(index: Int): Any? = error("Element-by-element removal")
                override fun add(index: Int, element: Any?) = error("Element-by-element insertion")
                override fun add(element: Any?): Boolean = error("Element-by-element append")
            }
            source.list = backed(json, List(100) { it }).list
            assertEquals(List(30) { it + 10 }, values(removed(source, 10, 30, "x")))
            removed(source, 10, 1, *Array<Any?>(30) { "added$it" })
            assertEquals(100, source.size)
            assertEquals(40, source[40])
            assertEquals(99, source[99])
        }
    }

    @Test
    fun exhaustiveSmallCasesMatchReferenceForBothBackends() {
        for (json in listOf(false, true)) {
            for (length in 0..5) {
                val initial = List<Any?>(length) { it }
                val starts = (-length - 2..length + 2).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)
                val deletes = (-2..length + 2).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)
                for (start in starts) for (delete in deletes) for (addCount in 0..4) {
                    val adds = Array<Any?>(addCount) { "add$it" }
                    val index = if (start < 0) {
                        (length.toLong() + start).coerceAtLeast(0).toInt()
                    } else {
                        start.coerceAtMost(length)
                    }
                    val count = delete.coerceIn(0, length - index)
                    val expectedRemoved = initial.subList(index, index + count)
                    val expected = initial.take(index) + adds.toList() + initial.drop(index + count)
                    val source = backed(json, initial)
                    val result = removed(source, start, delete, *adds)
                    val context = "json=$json length=$length start=$start delete=$delete adds=$addCount"
                    assertEquals(expectedRemoved, values(result), "removed: $context")
                    assertEquals(expected, values(source), "source: $context")
                }
            }
        }
    }
}
