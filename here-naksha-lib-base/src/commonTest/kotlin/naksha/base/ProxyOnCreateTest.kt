package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyOnCreateTest {

    @Test
    fun shouldInitializeDataWithOnCreate() {
        // Given:
        val obj = TestClassWithOnCreate()

        // Then
        assertEquals(TEST_VALUE, obj[TEST_KEY])
    }
}

const val TEST_KEY = "test_key"
const val TEST_VALUE = "test_value"

private class TestClassWithOnCreate : PAnyMap() {

    override fun onCreation() {
        super.onCreation()
        put(TEST_KEY, TEST_VALUE)
    }
}
