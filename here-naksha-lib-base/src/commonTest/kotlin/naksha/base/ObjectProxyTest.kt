package naksha.base

import kotlin.test.*

class Foo : P_Object() {
    var name: String by object : NotNullProperty<Any, Foo, String>(String::class, "Bernd") {}
    var age: Int by object : NotNullProperty<Any, Foo, Int>(Int::class, 0) {}
}

class Bar : P_Object() {
    var foo: Foo by object : NotNullProperty<Any, Bar, Foo>(Foo::class) {}
    var foo2 : Foo? by object : NullableProperty<Any, Bar, Foo>(Foo::class) {}
}

class ObjectProxyTest {
    @Test
    fun testNotNullable() {
        val bar = Bar()
        val foo = bar.foo
        assertNotNull(foo)
        assertSame(foo, bar.foo)
        bar.foo.age = 12
        assertEquals(12, bar.foo.age)
        assertFalse(bar.foo.hasRaw("name"))
        assertEquals("Bernd", bar.foo.name)
        assertTrue(bar.foo.hasRaw("name"))
        assertEquals("Bernd", bar.foo.getRaw("name"))
        bar.foo.name = "Hello World"
        assertEquals("Hello World", bar.foo.name)
    }

    @Test
    fun testNullable() {
        val bar = Bar()
        assertNull(bar.foo2)
        bar.foo2 = Foo()
        val foo2 = bar.foo2
        assertNotNull(foo2)
        assertSame(foo2, bar.foo2)
    }
}