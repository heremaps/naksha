package naksha.base

import kotlin.test.*

private class TestListInt : ListProxy<Int>(Int::class)
private class TestListString : ListProxy<String>(String::class)

class ListProxyTest {

    @Test
    fun shouldAddElements() {
        // given
        val list = TestListInt()

        // when
        list.add(11)
        // then
        assertEquals(11, list[0])

        // when
        list.add(null)
        // then
        assertEquals(null, list[1])
        assertEquals(2, list.size)

        var count = 0
        for (el in list) {
            count++
        }
        assertEquals(count, list.size)
    }

    @Test
    fun shouldAddElementAtIndex() {
        // given
        val list = TestListInt()
        list.add(11)
        list.add(12)
        list.add(13)

        // when
        list.add(1, 7)

        // then
        assertEquals(11, list[0])
        assertEquals(7, list[1])
        assertEquals(12, list[2])
        assertEquals(13, list[3])
    }

    @Test
    fun testAddIndexOutOfBounds() {
        // given
        val list = TestListInt()
        list.add(11)

        // expect
        assertFailsWith<RuntimeException> { list.add(-1, 12) }
        // Adding at the end should work!
        list.add(13, 12)
    }

    @Test
    fun testListSize() {
        // given
        val list = TestListString()
        // expect
        assertEquals(0, list.size)

        // when
        list.add("any")

        // then
        assertEquals(1, list.size)
    }

    @Test
    fun testAddAll() {
        // given
        val list = TestListInt()
        list.add(7)

        // when
        list.addAll(listOf(10, 11))

        // then
        assertEquals(3, list.size)
        assertEquals(10, list[1])
        assertEquals(11, list[2])
    }

    @Test
    fun testAddAllAtIndex() {
        // given
        val list = TestListInt()
        list.add(7)
        list.add(8)

        // when
        list.addAll(1, listOf(10, 11))

        // then
        assertEquals(4, list.size)
        assertEquals(7, list[0])
        assertEquals(10, list[1])
        assertEquals(11, list[2])
        assertEquals(8, list[3])
    }

    @Test
    fun testContainsAll() {
        // given
        val list = TestListInt()
        list.add(7)
        list.add(8)
        list.add(9)

        // expect
        assertTrue(list.containsAll(listOf(7)))
        assertTrue(list.containsAll(listOf(7, 9)))
        assertTrue(list.containsAll(listOf(7, 8, 9)))
        assertFalse(list.containsAll(listOf(5, 9)))
    }

    @Test
    fun testRemoveAll() {
        // given
        val list = TestListInt()
        list.add(7)
        list.add(8)
        list.add(9)

        // when
        val result = list.removeAll(listOf(8, 9))

        // then
        assertTrue(result)
        assertEquals(1, list.size)
        assertEquals(7, list[0])
    }

    @Test
    fun testRetainAll() {
        // given
        val list = TestListInt()
        list.add(7)
        list.add(8)
        list.add(9)

        // when
        val result = list.retainAll(listOf(8, 9))

        // then
        assertTrue(result)
        assertEquals(2, list.size)
        assertEquals(8, list[0])
        assertEquals(9, list[1])
    }

    @Test
    fun testSubList() {
        // given
        val list = TestListInt()
        list.add(7)
        list.add(8)
        list.add(9)
        list.add(10)

        // when
        val result = list.subList(1, 3)

        // then
        assertEquals(2, result.size)
        assertEquals(4, list.size)
        assertEquals(8, result[0])
        assertEquals(9, result[1])

        // when
        val result2 = list.subList(2, 2)
        assertTrue(result2.isEmpty())
    }

    @Test
    fun testMutableIterator() {
        // given
        val list = TestListInt()
        list.add(7)
        list.add(8)

        // when
        val iterator = list.listIterator()

        // then
        assertEquals(7, iterator.next())
        assertEquals(8, iterator.next())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }
}