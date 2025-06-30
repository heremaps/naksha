package naksha.base

import kotlin.test.*

class PropertyTest {
    class TestClass : AnyObject() {
        companion object TestBoolean_C {
            private val ADMIN = NotNullBoolProperty<TestClass>()
            private val FOO = NotNullBoolProperty<TestClass>(storeDefaultValue = true)
            private val BAR = NullableBoolProperty<TestClass>()
            private val COUNT = NotNullProperty<TestClass, Long>(Long_TYPE) { _,_ -> 0L }
        }
        var admin: Boolean by ADMIN
        var foo: Boolean by FOO
        var bar: Boolean? by BAR
        var count: Long by COUNT
    }

    @Test
    fun test_NotBooleanProperty() {
        val a = TestClass()
        assertFalse( a.admin )
        assertFalse( a.foo )
        assertNull( a.bar )
        assertEquals(0L, a.count )

        // Set true value.
        a.admin = true
        assertTrue( a.admin )
        a.foo = true
        assertTrue( a.foo )
        a.bar = true
        assertEquals( true, a.bar )

        // Set false value.
        a.admin = false
        assertFalse( a.admin )
        assertFalse( a.containsKey("admin") )
        a.foo = false
        assertFalse( a.foo )
        assertTrue( a.containsKey("foo") )
        a.bar = false
        assertEquals( false, a.bar )
        assertTrue( a.containsKey("bar") )

        // Set null value.
        a.bar = null
        assertNull( a.bar )
        assertFalse( a.containsKey("bar") )

        // Set long value
        a.count = 5L
        assertEquals(5L, a.count)
    }

    // TODO: Test the other properties !
}